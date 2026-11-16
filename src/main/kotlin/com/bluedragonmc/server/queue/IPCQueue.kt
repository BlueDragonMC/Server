package com.bluedragonmc.server.queue

import com.bluedragonmc.api.grpc.GsClient
import com.bluedragonmc.api.grpc.PlayerHolderOuterClass.SendPlayerRequest
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.GameRegistry
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.game.GameData
import com.bluedragonmc.server.game.GameRequest
import com.bluedragonmc.server.logging.LogContext
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
import java.util.*

object IPCQueue : Queue() {
    private val logger = LoggerFactory.getLogger(IPCQueue::class.java)

    override fun queue(player: Player, request: GameRequest) {
        player.sendMessage(Component.translatable("queue.adding", NamedTextColor.DARK_GRAY))
        Database.IO.launch {
            Messaging.outgoing.addToQueue(player, request.toProto())
        }
    }

    override fun bulkEnqueue(requests: List<Pair<Player, GameRequest>>) {
        requests.forEach { it.first.sendMessage(Component.translatable("queue.adding", NamedTextColor.DARK_GRAY)) }
        Database.IO.launch {
            Messaging.outgoing.bulkAddToQueue(requests.map { (player, request) -> player to request.toProto() })
        }
    }

    override fun start() {

    }

    override fun createInstance(request: GsClient.CreateInstanceRequest): Game {
        val start = System.nanoTime()
        val gameRequest = GameRequest.create(
            request.game,
            if (request.hasMode()) request.mode else null,
        )
        val mapSource = Maps.MapSource(
            request.mapSource.mapId,
            request.mapSource.mapUrl,
            request.mapSource.mapFormat,
            request.mapSource.mapConfig,
        )
        val game = GameLoader.createNewGame(GameData(gameRequest.game, mapSource, gameRequest.mode))
        LogContext.with(LogContext.game(game)) {
            logger.info("Created '${request.game}' game on map '${game.data.mapSource.id}' and mode '${game.data.mode.value}' with id '${game.id}'. (${(System.nanoTime() - start) / 1_000_000}ms)")
        }
        // The service will soon be notified of this instance's creation
        // once the mandatory MessagingModule is initialized
        return game
    }

    override fun sendPlayer(request: SendPlayerRequest) {
        val gameId = request.instanceId
        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(UUID.fromString(request.playerUuid)) ?: return
        val game = GameRegistry.findGame(gameId) ?: run {
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
        if (player.playerConnection.clientState != ConnectionState.PLAY) return
        if (GameRegistry.findGame(player) == game || player.instance == null) return
        player.sendMessage(
            Component.translatable(
                "queue.sending",
                NamedTextColor.DARK_GRAY,
                Component.text(gameId + "/" + instance.uuid.toString())
            )
        )
        LogContext.with(LogContext.player(player) + LogContext.game(game, instance)) {
            logger.info("Sending player ${player.username} to game '$gameId' and instance '${instance.uuid}'. (current instance: ${player.instance?.uuid})")
        }

        game.addPlayer(player)
    }
}