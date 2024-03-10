package com.bluedragonmc.server.queue

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.GsClient
import com.bluedragonmc.api.grpc.PlayerHolderOuterClass.SendPlayerRequest
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
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
        if (gameType.name == Environment.defaultGameName && gameType.mapName == null && gameType.mode == null) {
            lobby.addPlayer(player)
            return
        }
        player.sendMessage(Component.translatable("queue.adding", NamedTextColor.DARK_GRAY))
        Database.IO.launch {
            if (queuedPlayers.contains(player)) {
                Messaging.outgoing.removeFromQueue(player)
            } else {
                Messaging.outgoing.addToQueue(player, gameType)
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
        val start = System.nanoTime()
        val map = if (request.gameType.hasMapName()) request.gameType.mapName else randomMap(request.gameType.name)
        if (map == null) {
            logger.error("An instance request for ${request.gameType.name} was received, but no map name was provided and a random map was not found.")
            return null
        }
        val game = GameLoader.createNewGame(request.gameType.name, map, request.gameType.mode)
        logger.info("Created '${request.gameType.name}' game on map '$map' and mode '${game.mode}' with id '${game.id}'. (${(System.nanoTime() - start) / 1_000_000}ms)")
        // The service will soon be notified of this instance's creation
        // once the mandatory MessagingModule is initialized
        return game
    }

    override fun sendPlayer(request: SendPlayerRequest) {
        val gameId = request.instanceId
        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(UUID.fromString(request.playerUuid)) ?: return
        val game = Game.findGame(gameId) ?: run {
            player.sendMessage(
                Component.translatable(
                    "queue.error_sending",
                    NamedTextColor.RED,
                    Component.translatable("queue.error.no_game_found")
                )
            )
            return
        }
        val instance = game.getModule<InstanceModule>().getSpawningInstance(player)
        // Only allow players that have fully logged in, preventing them from being sent to the game twice
        if (player.playerConnection.connectionState != ConnectionState.PLAY) return
        if (Game.findGame(player) == game || player.instance == null) return
        player.sendMessage(
            Component.translatable(
                "queue.sending",
                NamedTextColor.DARK_GRAY,
                Component.text(gameId + "/" + instance.uniqueId.toString())
            )
        )
        logger.info("Sending player ${player.username} to game '$gameId' and instance '${instance.uniqueId}'. (current instance: ${player.instance?.uniqueId})")

        game.addPlayer(player)
    }
}