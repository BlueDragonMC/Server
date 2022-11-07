package com.bluedragonmc.server.queue

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.lobby
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import kotlin.random.Random

/**
 * A temporary queue system that exists only on the current Minestom server.
 * This is useful for development environments without an external queue service.
 */
class TestQueue : Queue() {
    private val queuedPlayers: Cache<Player, CommonTypes.GameType> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .build()

    private val logger = LoggerFactory.getLogger(TestQueue::class.java)

    /**
     * Adds the player to the queue.
     * @param player The player to add to the queue.
     * @param gameFilter Used to find existing games to add the player to.
     * @param idealGame If no games already exist, start a new one of this type. If this is null, the queue will not start a new game.
     */
    override fun queue(player: Player, gameType: CommonTypes.GameType) {
        if (gameType.name == "Lobby" && gameType.mapName.isEmpty() && gameType.mode.isEmpty()) {
            lobby.addPlayer(player)
            return
        }
        if (queuedPlayers.getIfPresent(player) != null) {
            player.sendMessage(Component.translatable("queue.removing", NamedTextColor.RED))
            queuedPlayers.invalidate(player)
            return
        }
        if (gameType.mapName.isNotEmpty()) {
            val mapExists = getMapNames(gameType.name).contains(gameType.mapName)
            if (!mapExists) {
                player.sendMessage(Component.translatable("queue.error.no_map_found", NamedTextColor.RED))
                return
            }
        }
        queuedPlayers.put(player, gameType)
        player.sendMessage(
            Component.translatable("queue.added", NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(Component.text("Test queue debug information\nGame type: $gameType")))
        )
    }

    /**
     * Adds a player to a game, regardless of their queue status.
     */
    fun join(player: Player, game: Game) {
        player.sendMessage(Component.translatable("queue.sending", NamedTextColor.GREEN, Component.text(game.instanceId?.toString().orEmpty())))
        game.addPlayer(player)
    }

    var instanceStarting = false // only one instance is allowed to start per queue cycle
    override fun start() {
        MinecraftServer.getSchedulerManager().buildTask {
            try {
                instanceStarting = false
                queuedPlayers.asMap().forEach { (player, gameType) ->
                    if (!games.containsKey(gameType.name)) {
                        player.sendMessage(
                            Component.translatable("queue.error.invalid_game_type", NamedTextColor.RED)
                        )
                        queuedPlayers.invalidate(player)
                        return@forEach
                    }
                    for (game in Game.games) {
                        if (game.name == gameType.name &&
                            (!gameType.selectorsList.contains(CommonTypes.GameType.GameTypeFieldSelector.MAP_NAME) || gameType.mapName == game.mapName)
                        ) {
                            logger.info("Found a good game for ${player.username} to join")
                            queuedPlayers.invalidate(player)
                            join(player, game)
                            return@forEach
                        }
                    }
                    if (instanceStarting) return@forEach
                    logger.info("Starting a new instance for ${player.username}")
                    player.sendMessage(Component.translatable("queue.creating_instance", NamedTextColor.GREEN))
                    val map = if (gameType.mapName.isNullOrEmpty()) randomMap(gameType.name)
                    else gameType.mapName
                    logger.info("Map chosen: $map")
                    try {
                        games[gameType.name]!!.call(map)
                        instanceStarting = true
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        player.sendMessage(Component.text(e.message.orEmpty(), NamedTextColor.RED))
                        queuedPlayers.invalidate(player)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.repeat(Duration.ofMillis(500)).schedule()
    }

    override fun getMaps(gameType: String): Array<File>? {
        val worldFolder = "worlds/$gameType"
        val file = File(worldFolder)
        if (!(file.exists() && file.isDirectory)) arrayOf<File>()
        return file.listFiles()
    }

    private fun getMapNames(gameType: String): ArrayList<String> {
        val maps = getMaps(gameType) ?: return arrayListOf()
        val mapNames = ArrayList<String>()
        for (map in maps) {
            mapNames.add(map.name)
        }
        return mapNames
    }

    override fun randomMap(gameType: String): String? {
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