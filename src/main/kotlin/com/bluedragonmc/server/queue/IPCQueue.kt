package com.bluedragonmc.server.queue

import com.bluedragonmc.messages.*
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.module.messaging.MessagingModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.network.ConnectionState
import org.slf4j.LoggerFactory
import java.io.File

object IPCQueue : Queue() {
    private val logger = LoggerFactory.getLogger(IPCQueue::class.java)

    private val queuedPlayers = mutableListOf<Player>()

    override fun queue(player: Player, gameType: GameType) {
        player.sendMessage(Component.translatable("queue.adding", NamedTextColor.DARK_GRAY))
        if (queuedPlayers.contains(player)) MessagingModule.publish(RequestRemoveFromQueueMessage(player.uuid))
        else MessagingModule.publish(RequestAddToQueueMessage(player.uuid, gameType))
    }

    override fun start() {
        MessagingModule.subscribe(RequestCreateInstanceMessage::class) { message ->
            if (message.containerId == MessagingModule.containerId) {
                logger.info("Received request to create instance with type ${message.gameType}.")
                val start = System.nanoTime()
                val constructor = games[message.gameType.name] ?: return@subscribe
                val map = message.gameType.mapName ?: randomMap(message.gameType.name) ?: run {
                    logger.error("No map name was specified and a random map was not found. A new instance cannot be created.")
                    return@subscribe
                }
                logger.info("Using map: '$map'")
                val game = constructor.invoke(map)
                val instance = game.getInstance()
                logger.info("Created instance ${instance.uniqueId} from type ${message.gameType}. (${(System.nanoTime() - start) / 1_000_000}ms)")
                // The service will soon be notified of this instance's creation
                // once the mandatory MessagingModule is initialized
            }
        }
        MessagingModule.subscribe(SendPlayerToInstanceMessage::class) { message ->
            val instance = MinecraftServer.getInstanceManager().getInstance(message.instance) ?: return@subscribe
            val player = MessagingModule.findPlayer(message.player) ?: return@subscribe
            // Only allow players that have fully logged in, preventing them from being sent to the game twice
            if (player.playerConnection.connectionState != ConnectionState.PLAY) return@subscribe
            if (player.instance == instance || player.instance == null) return@subscribe
            player.sendMessage(Component.translatable("queue.sending", NamedTextColor.DARK_GRAY, Component.text(message.instance.toString())))
            logger.info("Sending player ${player.username} to instance ${message.instance}. (current instance: ${player.instance?.uniqueId})")
            val game = Game.findGame(instance.uniqueId) ?: run {
                player.sendMessage(Component.translatable("queue.error_sending", NamedTextColor.RED, Component.translatable("queue.error.no_game_found")))
                return@subscribe
            }
            game.addPlayer(player)
        }
    }

    private fun getMaps(gameType: String): Array<File>? {
        val worldFolder = "worlds/$gameType"
        val file = File(worldFolder)
        if (!(file.exists() && file.isDirectory)) arrayOf<File>()
        return file.listFiles()
    }

    private fun randomMap(gameType: String): String? = getMaps(gameType)?.randomOrNull()?.name

}