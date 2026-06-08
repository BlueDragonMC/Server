package com.bluedragonmc.server.queue

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.GsClient
import com.bluedragonmc.api.grpc.PlayerHolderOuterClass.SendPlayerRequest
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.game.GameData
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Maps
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

    override fun createInstance(request: GsClient.CreateInstanceRequest): Game {
        val start = System.nanoTime()
        val game = GameLoader.createNewGame(GameData(request.game, request.mapSource.let {
            Maps.MapSource(it.mapId, it.mapUrl, it.mapFormat, it.mapConfig)
        }, if (request.hasMode()) request.mode else null))
        logger.info("Created '${request.game}' game on map '${game.data.mapSource.id}' and mode '${game.data.mode}' with id '${game.id}'. (${(System.nanoTime() - start) / 1_000_000}ms)")
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
                Component.text(gameId + "/" + instance.uuid.toString())
            )
        )
        logger.info("Sending player ${player.username} to game '$gameId' and instance '${instance.uuid}'. (current instance: ${player.instance?.uuid})")

        game.addPlayer(player)
    }
}