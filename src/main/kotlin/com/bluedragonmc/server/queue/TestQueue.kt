package com.bluedragonmc.server.queue

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.game.Lobby
import com.bluedragonmc.server.game.TeamDeathmatchGame
import com.bluedragonmc.server.game.WackyMazeGame
import com.bluedragonmc.server.module.gameplay.SpawnpointModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.event.HoverEventSource
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.time.Duration
import java.util.function.Predicate

// TODO redo this entire system because it sucks (but without the predicate, just use the string for the game)

/**
 * A temporary queue system that exists only on the current Minestom server.
 * This is not a fully fledged queue system, but it will work fine for now.
 */
class TestQueue {
    private val queuedPlayers: HashMap<Player, String> = hashMapOf()

    val gameClasses = hashMapOf(
        "WackyMaze" to WackyMazeGame::class.java,
        "TeamDeathmatch" to TeamDeathmatchGame::class.java,
    )

    /**
     * Adds the player to the queue.
     * @param player The player to add to the queue.
     * @param gameFilter Used to find existing games to add the player to.
     * @param idealGame If no games already exist, start a new one of this type. If this is null, the queue will not start a new game.
     */
    fun queue(player: Player, gameType: String) {
        if (queuedPlayers.contains(player)) {
            player.sendMessage(Component.text("Removing you from the queue..."))
            queuedPlayers.remove(player)
            return
        }
        queuedPlayers[player] = gameType
        player.sendMessage(Component.text("You are now in the queue.", NamedTextColor.GREEN).hoverEvent(HoverEvent.showText(Component.text("Test queue debug information\nGame type: $gameType"))))
    }

    /**
     * Adds a player to a game, regardless of their queue status.
     * Removes the player from the queue.
     */
    fun join(player: Player, game: Game) {
        queuedPlayers.remove(player)
        player.sendMessage(Component.text("You are being sent to a game.", NamedTextColor.GREEN))
        game.players.add(player)
        if (!game.hasModule<SpawnpointModule>())
            player.setInstance(game.getInstance())
        else
            player.setInstance(game.getInstance(), game.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(player))
    }


    var instanceStarting = false // only one instance is allowed to start per queue cycle
    fun start() {
        MinecraftServer.getSchedulerManager().buildTask {
            try {
                instanceStarting = false
                queuedPlayers.forEach { (player, gameType) ->
                    if (!gameClasses.containsKey(gameType)) {
                        player.sendMessage(Component.text("Invalid game type. Removing you from the queue.", NamedTextColor.RED))
                        queuedPlayers.remove(player)
                        return@forEach
                    }
                    for (game in Game.games) {
                        if (game.name == gameType) {
                            println("Found a good game for ${player.username} to join")
                            join(player, game)
                            return@forEach
                        }
                    }
                    if (instanceStarting) return@forEach
                    println("Starting a new instance for ${player.username}")
                    player.sendMessage(
                        Component.text(
                            "No joinable instance found. Creating a new instance for you.",
                            NamedTextColor.GREEN
                        )
                    )
                    gameClasses[gameType]!!.getDeclaredConstructor().newInstance()
                    instanceStarting = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.repeat(Duration.ofSeconds(2)).schedule()
    }

}