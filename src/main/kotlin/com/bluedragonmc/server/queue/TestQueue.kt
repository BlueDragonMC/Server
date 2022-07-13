package com.bluedragonmc.server.queue

import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.gameplay.SpawnpointModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.io.File
import java.time.Duration
import kotlin.random.Random

/**
 * A temporary queue system that exists only on the current Minestom server.
 * This is not a fully fledged queue system, but it will work fine for now.
 */
class TestQueue : Queue() {
    private val queuedPlayers: HashMap<Player, String> = hashMapOf()

    /**
     * Adds the player to the queue.
     * @param player The player to add to the queue.
     * @param gameFilter Used to find existing games to add the player to.
     * @param idealGame If no games already exist, start a new one of this type. If this is null, the queue will not start a new game.
     */
    override fun queue(player: Player, gameType: GameType) {
        if (queuedPlayers.contains(player)) {
            player.sendMessage(Component.text("Removing you from the queue..."))
            queuedPlayers.remove(player)
            return
        }
        queuedPlayers[player] = gameType.name
        player.sendMessage(Component.text("You are now in the queue.", NamedTextColor.GREEN).hoverEvent(HoverEvent.showText(Component.text("Test queue debug information\nGame type: $gameType"))))
    }

    /**
     * Adds a player to a game, regardless of their queue status.
     */
    fun join(player: Player, game: Game) {
        player.sendMessage(Component.text("You are being sent to a game.", NamedTextColor.GREEN))
        game.players.add(player)
        if (!game.hasModule<SpawnpointModule>())
            player.setInstance(game.getInstance())
        else
            player.setInstance(game.getInstance(), game.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(player))
    }


    var instanceStarting = false // only one instance is allowed to start per queue cycle
    override fun start() {
        MinecraftServer.getSchedulerManager().buildTask {
            try {
                val playersToRemove = mutableListOf<Player>()
                instanceStarting = false
                queuedPlayers.forEach { (player, gameType) ->
                    if (!gameClasses.containsKey(gameType)) {
                        player.sendMessage(Component.text("Invalid game type. Removing you from the queue.", NamedTextColor.RED))
                        playersToRemove.add(player)
                        return@forEach
                    }
                    for (game in Game.games) {
                        if (game.name == gameType) {
                            println("Found a good game for ${player.username} to join")
                            playersToRemove.add(player)
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
                    val map = randomMap(gameType)
                    println("Map chosen: $map")
                    gameClasses[gameType]!!.call(map)
                    instanceStarting = true
                }
                playersToRemove.forEach { queuedPlayers.remove(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.repeat(Duration.ofSeconds(2)).schedule()
    }

    fun getMaps(gameType: String): Array<File>? {
        val worldFolder = "worlds/$gameType"
        val file = File(worldFolder)
        if (!(file.exists() && file.isDirectory)) arrayOf<File>()
        return file.listFiles()
    }

    fun getMapNames(gameType: String): ArrayList<String> {
        val maps = getMaps(gameType) ?: return arrayListOf()
        val mapNames = ArrayList<String>()
        for (map in maps) {
            mapNames.add(map.name)
        }
        return mapNames
    }

    fun randomMap(gameType: String): String? {
        val allMaps = getMaps(gameType)
        if (allMaps != null) return allMaps[Random.nextInt(allMaps.size)].name
        return null
    }

    fun randomMapOrDefault(gameType: String, defaultMap: String): String {
        val map = randomMap(gameType)
        if (map == null) return defaultMap
        else return map
    }

}