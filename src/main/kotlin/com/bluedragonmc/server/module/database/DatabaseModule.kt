package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.packet.PacketUtils
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Filters
import kotlinx.coroutines.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.trait.CancellableEvent
import net.minestom.server.event.trait.PlayerEvent
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.path
import org.litote.kmongo.reactivestreams.KMongo
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class DatabaseModule : GameModule() {

    companion object {

        val IO = object : CoroutineScope {
            override val coroutineContext: CoroutineContext =
                Dispatchers.IO + SupervisorJob() + CoroutineName("Database IO")
        }

        private val client: CoroutineClient by lazy {
            KMongo.createClient(MongoClientSettings.builder()
                .applyConnectionString(ConnectionString("mongodb://${Environment.current.mongoHostname}"))
                .applyToSocketSettings { block ->
                    block.connectTimeout(5, TimeUnit.SECONDS)
                }
                .applyToClusterSettings { block ->
                    block.serverSelectionTimeout(5, TimeUnit.SECONDS)
                }
                .build()
            ).coroutine
        }
        private val database: CoroutineDatabase by lazy {
            client.getDatabase("bluedragon")
        }
        private val logger = LoggerFactory.getLogger(Companion::class.java)

        internal fun getPlayersCollection(): CoroutineCollection<PlayerDocument> = database.getCollection("players")
        internal fun getGroupsCollection(): CoroutineCollection<PermissionGroup> = database.getCollection("groups")
        internal fun getMapsCollection(): CoroutineCollection<MapData> = database.getCollection("maps")

        internal suspend fun getPlayerDocument(username: String): PlayerDocument? {
            MinecraftServer.getConnectionManager().findPlayer(username)?.let {
                return (it as CustomPlayer).data
            }
            return getPlayersCollection().findOne(PlayerDocument::usernameLower eq username.lowercase())
        }

        internal suspend fun getNameColor(username: String): TextColor? {
            val document = getPlayerDocument(username)
            return document?.highestGroup?.color
        }

        internal suspend fun getNameColor(uuid: UUID): TextColor? {
            val document = getPlayerDocument(uuid)
            return document?.highestGroup?.color
        }

        internal suspend fun getPlayerDocument(uuid: UUID): PlayerDocument? {
            MinecraftServer.getConnectionManager().getPlayer(uuid)?.let {
                return (it as CustomPlayer).data
            }
            return getPlayersCollection().findOne(Filters.eq(PlayerDocument::uuid.path(), uuid.toString()))
        }

        internal suspend fun getAllGroups(): List<PermissionGroup> {
            val result = mutableListOf<PermissionGroup>()
            getGroupsCollection().find().consumeEach {
                result.add(it)
            }
            return result
        }

        private suspend fun getPlayerDocument(player: Player): PlayerDocument {
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

        init {
            MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerPreLoginEvent::class.java) { event ->
                // Load players' data from the database when they spawn
                val player = event.player as CustomPlayer
                if (!player.isDataInitialized()) IO.launch {
                    try {
                        player.data = getPlayerDocument(player)
                    } catch (e: Throwable) {
                        logger.error("Player data for ${player.username} failed to load.")
                        MinecraftServer.getExceptionManager().handleException(e)
                        player.kick(Component.translatable("module.database.data_load_fail", NamedTextColor.RED))
                    }
                    if (player.username != player.data.username || player.data.username.isBlank()) {
                        // Keep an up-to-date record of player usernames
                        player.data.update(PlayerDocument::username, player.username)
                        player.data.update(PlayerDocument::usernameLower, player.username.lowercase())
                        logger.info("Updated username for ${player.uuid}: ${player.data.username} -> ${player.username}")
                    }
                    if (player.data.usernameLower != player.username.lowercase()) {
                        player.data.update(PlayerDocument::usernameLower, player.username.lowercase())
                    }
                    MinecraftServer.getGlobalEventHandler().call(DataLoadedEvent(player))
                    logger.info("Loaded player data for ${player.username}")
                }
            }
        }
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        // Prevent player actions until data is loaded
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (!(event.player as CustomPlayer).isDataInitialized()) {
                event.newPosition = event.player.position
                event.player.sendPacket(
                    PacketUtils.getRelativePosLookPacket(event.player, event.player.position)
                )
            }
        }
        eventNode.addListener(PlayerChatEvent::class.java, ::preventIfDataNotLoaded)
    }

    private fun preventIfDataNotLoaded(event: PlayerEvent) {
        event as CancellableEvent
        if (!(event.player as CustomPlayer).isDataInitialized()) event.isCancelled = true
    }

    private val cachedMapData = hashMapOf<String, MapData?>()
    suspend fun getMapOrNull(mapName: String): MapData? = cachedMapData.getOrPut(mapName) {
        getMapsCollection().findOneById(mapName)
    }

    class DataLoadedEvent(private val player: Player) : PlayerEvent {
        override fun getPlayer(): Player = player
    }
}
