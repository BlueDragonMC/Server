package com.bluedragonmc.server.impl

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.api.DatabaseConnection
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.model.GameDocument
import com.bluedragonmc.server.model.PlayerDocument
import com.bluedragonmc.server.model.Punishment
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.elemMatch
import org.litote.kmongo.eq
import org.litote.kmongo.path
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.setValue
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.KMutableProperty

internal class DatabaseConnectionImpl(connectionString: String) : DatabaseConnection {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val client: CoroutineClient by lazy {
        KMongo.createClient(MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))
            .applyToSocketSettings { block ->
                block.connectTimeout(5, TimeUnit.SECONDS)
            }
            .applyToClusterSettings { block ->
                block.serverSelectionTimeout(5, TimeUnit.SECONDS)
            }
            .build()).coroutine
    }

    init {
        MinecraftServer.getSchedulerManager().buildShutdownTask {
            client.close()
        }
    }

    private val database: CoroutineDatabase by lazy {
        client.getDatabase(Environment.dbName)
    }

    private fun getPlayersCollection(): CoroutineCollection<PlayerDocument> = database.getCollection("players")
    private fun getGamesCollection(): CoroutineCollection<GameDocument> = database.getCollection("games")

    override suspend fun getPlayerDocument(username: String): PlayerDocument? {
        MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(username)?.let {
            return (it as CustomPlayer).data
        }
        return getPlayersCollection().findOne(PlayerDocument::usernameLower eq username.lowercase())
    }

    override suspend fun getPlayerDocument(uuid: UUID): PlayerDocument? {
        MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.let {
            return (it as CustomPlayer).data
        }
        return getPlayersCollection().findOne(Filters.eq(PlayerDocument::uuid.path(), uuid.toString()))
    }

    override suspend fun getPlayerDocument(player: Player): PlayerDocument {
        val col = getPlayersCollection()
        val foundDocument = col.findOneById(player.uuid.toString())
        return if (foundDocument == null) {
            // Create a document if it doesn't exist
            val newDocument = PlayerDocument(uuid = player.uuid)
            col.insertOne(newDocument)
            newDocument
        } else {
            foundDocument
        }
    }

    override fun loadDataDocument(player: CustomPlayer) {
        // Load players' data from the database when they spawn
        if (player.isDataInitialized()) return
        runBlocking {
            try {
                player.data = getPlayerDocument(player)

                if (player.username != player.data.username || player.data.username.isBlank()) {
                    // Keep an up-to-date record of player usernames
                    player.data.update(PlayerDocument::username, player.username)
                    player.data.update(PlayerDocument::usernameLower, player.username.lowercase())
                    logger.info("Updated username for ${player.uuid}: ${player.data.username} -> ${player.username}")
                }
                if (player.data.usernameLower != player.username.lowercase()) {
                    player.data.update(PlayerDocument::usernameLower, player.username.lowercase())
                }
                player.data.update(PlayerDocument::lastJoinDate, System.currentTimeMillis())
                MinecraftServer.getGlobalEventHandler().call(DataLoadedEvent(player))
                logger.info("Loaded player data for ${player.username}")
            } catch (e: Throwable) {
                logger.error("Player data for ${player.username} failed to load.")
                MinecraftServer.getExceptionManager().handleException(e)
                player.sendPacket(
                    LoginDisconnectPacket(
                        Component.translatable(
                            "module.database.data_load_fail",
                            NamedTextColor.RED
                        )
                    )
                )
                player.playerConnection.disconnect()
            }
        }
    }

    override suspend fun rankPlayersByStatistic(key: String, sortCriteria: String, limit: Int): List<PlayerDocument> {

        val sort = when (sortCriteria) {
            "ASC" -> Sorts.ascending("statistics.$key")
            "DESC" -> Sorts.descending("statistics.$key")
            else -> error("Invalid sort order")
        }

        return getPlayersCollection()
            .find("{\"statistics.$key\": {\$exists: true}}")
            .sort(sort)
            .limit(limit)
            .toList()
            .filter { it.statistics.containsKey(key) }
    }

    override suspend fun getPlayerForPunishmentId(id: String): PlayerDocument? {
        return getPlayersCollection()
            .findOne(PlayerDocument::punishments elemMatch Filters.regex(Punishment::id.path(), "^$id"))
    }

    override suspend fun <T> updatePlayer(playerUuid: String, field: KMutableProperty<T>, value: T) {
        getPlayersCollection().updateOneById(playerUuid, setValue(field, value))
    }

    override suspend fun logGame(game: GameDocument) {
        getGamesCollection().insertOne(game)
    }
}