package com.bluedragonmc.server

import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.InstanceUtils
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.ExecutionType
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

        game.recorder.log()

        state = GameState.ENDING
        GameRegistry.remove(game)

        val instancesToRemove = MinecraftServer.getInstanceManager().instances.filter { game.ownsInstance(it) }

        // the NotifyInstanceRemovedMessage is published when the MessagingModule is unregistered
        while (game.modules.isNotEmpty()) game.unregister(game.modules.first())

        if (queueAllPlayers) {
            val gameType = gameType {
                name = game.data.name
                if (game.data.mode != null) {
                    mode = game.data.mode
                }
            }
            Environment.queue.bulkEnqueue(game.players.map { it to gameType })
        }

        MinecraftServer.getSchedulerManager().buildTask {
            instancesToRemove.forEach { instance ->
                if (instance.isRegistered) {
                    logger.info("Forcefully unregistering instance ${instance.uuid}...")
                    InstanceUtils.forceUnregisterInstance(instance)
                }
            }
        }.executionType(ExecutionType.TICK_START).delay(Duration.ofSeconds(10))

        game.roster.clear()
        game.events.detach()
    }
}
