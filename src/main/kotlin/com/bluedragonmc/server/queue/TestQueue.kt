package com.bluedragonmc.server.queue

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.game.GameData
import com.bluedragonmc.server.service.Maps
import com.bluedragonmc.server.service.Messaging
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.reflect.jvm.jvmName

/**
 * A temporary queue system that exists only on the current Minestom server.
 * This is useful for development environments without an external queue service.
 */
class TestQueue : Queue() {
    private val queuedPlayers: Cache<Player, CommonTypes.GameType> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .build()

    private val logger = LoggerFactory.getLogger(TestQueue::class.java)

    private lateinit var maps: List<Maps.MapSource>

    /**
     * Adds the player to the queue.
     * @param player The player to add to the queue.
     * @param gameType The game type which the player wants to join.
     */
    override fun queue(player: Player, gameType: CommonTypes.GameType) {
        if (queuedPlayers.getIfPresent(player) != null) {
            player.sendMessage(Component.translatable("queue.removing", NamedTextColor.RED))
            queuedPlayers.invalidate(player)
            return
        }
        if (gameType.hasMapId()) {
            val mapExists =
                maps.any { map -> map matches gameType }
            if (!mapExists) {
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
        player.sendMessage(
            Component.translatable(
                "queue.sending",
                NamedTextColor.GREEN,
                Component.text(game.id)
            )
        )
        game.addPlayer(player)
    }

    private var instanceStarting = false // only one instance is allowed to start per queue cycle
    override fun start() {
        maps = runBlocking {
            Messaging.outgoing.getAvailableMaps(null, null, null).mapsList.map {
                Maps.MapSource(it.mapId, it.mapUrl, it.mapFormat, it.mapConfig)
            }
        }
        MinecraftServer.getSchedulerManager().buildTask {
            try {
                instanceStarting = false
                queuedPlayers.asMap().forEach { (player, gameType) ->
                    if (!GameLoader.gameNames.contains(gameType.name)) {
                        player.sendMessage(
                            Component.translatable("queue.error.invalid_game_type", NamedTextColor.RED)
                        )
                        queuedPlayers.invalidate(player)
                        return@forEach
                    }
                    val game = Game.games.firstOrNull {
                        it.data.name == gameType.name
                            && (it.data.mapSource matches gameType)
                            && (it.data.mapSource.isPlayerAllowed(player.uuid))
                            && (!gameType.hasMode() || gameType.mode == it.data.mode)
                    }
                    if (game != null) {
                        logger.info("Found a good game for ${player.username} to join")
                        join(player, game)
                        queuedPlayers.invalidate(player)
                        return@forEach
                    }
                    if (instanceStarting) return@forEach
                    logger.info("Starting a new instance for ${player.username}")
                    player.sendMessage(Component.translatable("queue.creating_instance", NamedTextColor.GREEN))
                    val map = maps.filter { map -> map matches gameType && map.isPlayerAllowed(player.uuid) }.random()
                    logger.info("Map chosen: ${map.id}")
                    try {
                        GameLoader.createNewGame(
                            GameData(
                                gameType.name,
                                map,
                                gameType.mode
                            )
                        )
                        instanceStarting = true
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        player.sendMessage(
                            Component.text(
                                e::class.jvmName + ": " + e.message.orEmpty(),
                                NamedTextColor.RED
                            )
                        )
                        queuedPlayers.invalidate(player)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.repeat(Duration.ofMillis(500)).schedule()
    }

}