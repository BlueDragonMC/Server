package com.bluedragonmc.server

import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.game.GameRequest
import com.bluedragonmc.server.logging.LogContext
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.InstanceUtils
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance
import net.minestom.server.timer.ExecutionType
import net.minestom.server.timer.TaskSchedule
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Manages the lifecycle and [GameState] of a single [Game].
 */
class GameLifecycle(private val game: Game) {

    private val logger = LoggerFactory.getLogger(GameLifecycle::class.java)

    private val creationTime = System.currentTimeMillis()

    var state: GameState = GameState.SERVER_STARTING
        set(value) {
            game.callCancellable(GameStateChangedEvent(game, field, value)) {
                field = value
            }
        }

    fun isInactive(): Boolean {
        // Games with players are always considered active
        if (game.players.isNotEmpty()) return false
        // Games without players are always inactive after 5 minutes
        return System.currentTimeMillis() - creationTime >= 1_000 * 60 * 5
    }

    fun endLater(delay: Duration = Duration.ZERO) {
        state = GameState.ENDING
        GameRegistry.remove(game)
        MinecraftServer.getSchedulerManager().buildTask {
            game.endGame()
        }.delay(delay).schedule()
    }

    fun end(queueAllPlayers: Boolean = true) {
        try {
            game.recorder.log()
            state = GameState.ENDING
        } finally {
            teardown(
                queueAllPlayers = queueAllPlayers,
                instanceRemovalDelay = TaskSchedule.duration(Duration.ofSeconds(10)),
            )
        }
    }

    /**
     * Cleans up a game in response to a failure in [Game.init].
     */
    fun abortInitialization() {
        teardown(queueAllPlayers = false, instanceRemovalDelay = TaskSchedule.nextTick())
    }

    /**
     * Releases all resources owned by a game.
     */
    private fun teardown(queueAllPlayers: Boolean, instanceRemovalDelay: TaskSchedule) {
        val instancesToRemove = try {
            game.getOwnedInstances()
        } catch (e: Exception) {
            LogContext.with(LogContext.game(game)) {
                logger.warn("Could not collect instances owned by game ${game.id}", e)
            }
            emptyList()
        }

        GameRegistry.remove(game)

        // the NotifyInstanceRemovedMessage is published when the MessagingModule is unregistered

        // Unregister modules in reverse registration order. Provider modules (players,
        // state, info, events) are registered first, so they need to remain available
        // while the other modules deinitialize.
        for (module in game.modules.toList().asReversed()) {
            if (!game.modules.contains(module)) continue
            try {
                game.unregister(module)
            } catch (e: Exception) {
                LogContext.with(LogContext.game(game)) {
                    logger.warn(
                        "Failed to unregister module ${module::class.simpleName} of game ${game.id}",
                        e
                    )
                }
            }
        }

        if (queueAllPlayers) {
            try {
                val request = GameRequest(game.data.name, game.data.mode)
                Environment.queue.bulkEnqueue(game.players.map { it to request })
            } catch (e: Exception) {
                LogContext.with(LogContext.game(game)) {
                    logger.error("Failed to queue players from game ${game.id}", e)
                }
            }
        }

        scheduleInstanceRemoval(instancesToRemove, instanceRemovalDelay)

        game.roster.clear()
        game.events.detach()
    }

    private fun scheduleInstanceRemoval(instances: List<Instance>, delay: TaskSchedule) {
        if (instances.isEmpty()) return

        val gameId = game.id
        val logger = logger
        MinecraftServer.getSchedulerManager().buildTask {
            instances.forEach { instance ->
                if (!instance.isRegistered) return@forEach
                logger.info("Forcefully unregistering instance ${instance.uuid}...")
                try {
                    InstanceUtils.forceUnregisterInstance(instance)
                } catch (e: Exception) {
                    logger.warn("Failed to unregister instance ${instance.uuid} of game $gameId", e)
                }
            }
        }.executionType(ExecutionType.TICK_START).delay(delay).schedule()
    }
}
