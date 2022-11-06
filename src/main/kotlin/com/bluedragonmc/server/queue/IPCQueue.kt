package com.bluedragonmc.server.queue

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.GsClient
import com.bluedragonmc.api.grpc.PlayerHolderOuterClass.SendPlayerRequest
import com.bluedragonmc.api.grpc.addToQueueRequest
import com.bluedragonmc.api.grpc.removeFromQueueRequest
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.messaging.MessagingModule
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.network.ConnectionState
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

object IPCQueue : Queue() {
    private val logger = LoggerFactory.getLogger(IPCQueue::class.java)

    private val queuedPlayers = mutableListOf<Player>()

    override fun queue(player: Player, gameType: CommonTypes.GameType) {
        if (gameType.name == "Lobby" && gameType.mapName == null && gameType.mode == null) {
            lobby.addPlayer(player)
            return
        }
        player.sendMessage(Component.translatable("queue.adding", NamedTextColor.DARK_GRAY))
        DatabaseModule.IO.launch {
            if (queuedPlayers.contains(player)) {
                MessagingModule.Stubs.queueStub.removeFromQueue(removeFromQueueRequest {
                    playerUuid = player.uuid.toString()
                })
            } else {
                MessagingModule.Stubs.queueStub.addToQueue(addToQueueRequest {
                    playerUuid = player.uuid.toString()
                    this.gameType = gameType
                })
            }
        }
    }

    override fun start() {

    }

    override fun getMaps(gameType: String): Array<File>? {
        val worldFolder = "worlds/$gameType"
        val file = File(worldFolder)
        if (!(file.exists() && file.isDirectory)) arrayOf<File>()
        return file.listFiles()
    }

    override fun randomMap(gameType: String): String? = getMaps(gameType)?.randomOrNull()?.name

    override fun createInstance(request: GsClient.CreateInstanceRequest): Game? {
        logger.info("Received request to create instance with type ${request.gameType}.")
        val start = System.nanoTime()
        val constructor = games[request.gameType.name] ?: return null
        val map = request.gameType.mapName ?: randomMap(request.gameType.name) ?: run {
            logger.error("No map name was specified and a random map was not found. A new instance cannot be created.")
            return null
        }
        logger.info("Using map: '$map'")
        val game = constructor.invoke(map)
        val instance = game.getInstance()
        logger.info("Created instance ${instance.uniqueId} from type ${request.gameType}. (${(System.nanoTime() - start) / 1_000_000}ms)")
        // The service will soon be notified of this instance's creation
        // once the mandatory MessagingModule is initialized
        return game
    }

    override fun sendPlayer(request: SendPlayerRequest) {
        val uuid = UUID.fromString(request.instanceId)
        val instance = MinecraftServer.getInstanceManager().getInstance(uuid) ?: return
        val player = MessagingModule.findPlayer(UUID.fromString(request.playerUuid)) ?: return
        // Only allow players that have fully logged in, preventing them from being sent to the game twice
        if (player.playerConnection.connectionState != ConnectionState.PLAY) return
        if (player.instance == instance || player.instance == null) return
        player.sendMessage(
            Component.translatable(
                "queue.sending",
                NamedTextColor.DARK_GRAY,
                Component.text(uuid.toString())
            )
        )
        logger.info("Sending player ${player.username} to instance ${request.instanceId}. (current instance: ${player.instance?.uniqueId})")
        val game = Game.findGame(instance.uniqueId) ?: run {
            player.sendMessage(
                Component.translatable(
                    "queue.error_sending",
                    NamedTextColor.RED,
                    Component.translatable("queue.error.no_game_found")
                )
            )
            return
        }
        game.addPlayer(player)
    }

}