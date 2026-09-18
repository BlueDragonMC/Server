package com.bluedragonmc.server

import com.bluedragonmc.server.utils.InstanceUtils
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.tag.Tag
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Global registry of all [Game] instances running on this server, along with
 * the background tasks that clean up inactive games and orphan instances.
 */
object GameRegistry {

    private val logger = LoggerFactory.getLogger(GameRegistry::class.java)

    private val _games: MutableList<Game> = CopyOnWriteArrayList()
    val games: List<Game> = _games

    /**
     * Instances will be cleaned up every 10 seconds (by default).
     */
    private val INSTANCE_CLEANUP_PERIOD = System.getenv("SERVER_INSTANCE_CLEANUP_PERIOD")?.toLongOrNull() ?: 10_000L

    /**
     * Instances must be inactive for at least 2 minutes
     * to be cleaned up (by default).
     */
    private val CLEANUP_MIN_INACTIVE_TIME =
        System.getenv("SERVER_INSTANCE_MIN_INACTIVE_TIME")?.toLongOrNull() ?: 120_000L

    private val INACTIVE_SINCE_TAG = Tag.Long("instance_inactive_since")

    internal fun add(game: Game) {
        _games.add(game)
    }

    internal fun remove(game: Game) {
        _games.remove(game)
    }

    fun findGame(player: Player): Game? =
        games.find { player in it.players || it.ownsInstance(player.instance ?: return@find false) }

    fun findGame(instanceId: UUID): Game? {
        val instance = MinecraftServer.getInstanceManager().getInstance(instanceId) ?: return null
        return games.find { it.ownsInstance(instance) }
    }

    fun findGame(gameId: String): Game? = games.find { it.id == gameId }

    init {
        MinecraftServer.getSchedulerManager().buildShutdownTask {
            ArrayList(games).forEach { game ->
                game.endGame(false)
            }
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val instances = MinecraftServer.getInstanceManager().instances

            games.forEach { game ->
                if (game.isInactive()) {
                    logger.info("Ending inactive game ${game.id} (${game.data})")
                    game.endGame(false)
                }
                game.removeDisconnectedPlayers()
            }

            instances.forEach { instance ->
                val owner = games.find { it.ownsInstance(instance) }
                if (owner != null || games.any { instance in it.getRequiredInstances() }) {
                    return@forEach
                }
                if (!instance.hasTag(INACTIVE_SINCE_TAG)) {
                    instance.setTag(INACTIVE_SINCE_TAG, System.currentTimeMillis())
                    return@forEach
                }
                if (System.currentTimeMillis() - instance.getTag(INACTIVE_SINCE_TAG) <= CLEANUP_MIN_INACTIVE_TIME) {
                    return@forEach
                }
                logger.info("Removing orphan instance ${instance.uuid} (${instance})")
                InstanceUtils.forceUnregisterInstance(instance)
            }
        }.repeat(Duration.ofMillis(INSTANCE_CLEANUP_PERIOD)).schedule()
    }
}
