package com.bluedragonmc.server.queue

import com.bluedragonmc.messages.*
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.game.BedWarsGame
import com.bluedragonmc.server.game.TeamDeathmatchGame
import com.bluedragonmc.server.game.WackyMazeGame
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.utils.broadcast
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player

object IPCQueue {

    internal val gameClasses = hashMapOf(
        "WackyMaze" to ::WackyMazeGame,
        "TeamDeathmatch" to ::TeamDeathmatchGame,
        "BedWars" to ::BedWarsGame,
    )

    private val queuedPlayers = mutableListOf<Player>()

    fun queue(player: Player, gameType: GameType) {
        player.sendMessage(Component.text("Adding you to the queue...", NamedTextColor.DARK_GRAY))
        if(queuedPlayers.contains(player)) MessagingModule.publish(RequestRemoveFromQueueMessage(player.uuid))
        else MessagingModule.publish(RequestAddToQueueMessage(player.uuid, gameType))
    }

    fun start() {
        MessagingModule.subscribe(RequestCreateInstanceMessage::class) { message ->
            if (message.containerId == MessagingModule.containerId) {
                val constructor = gameClasses[message.gameType.name] ?: return@subscribe
                val map = message.gameType.mapName ?: return@subscribe
                val game = constructor.invoke(map)
                val instance = game.getInstance()
                broadcast(Component.text("Created instance ${instance.uniqueId} from type ${message.gameType}.", NamedTextColor.DARK_GRAY))
                // The service will soon be notified of this instance's creation
                // once the mandatory MessagingModule is initialized
            }
        }
        MessagingModule.subscribe(SendPlayerToInstanceMessage::class) { message ->
            val instance = MinecraftServer.getInstanceManager().getInstance(message.instance) ?: return@subscribe
            val player = MessagingModule.findPlayer(message.player) ?: return@subscribe
            player.sendMessage(Component.text("Sending you to ${message.instance}...", NamedTextColor.DARK_GRAY))
            val game = Game.findGame(instance.uniqueId) ?: run {
                player.sendMessage(Component.text("There was an error sending you to the instance! (No game found)", NamedTextColor.RED))
                return@subscribe
            }
            game.players.add(player)
            player.setInstance(instance)
        }
    }
}