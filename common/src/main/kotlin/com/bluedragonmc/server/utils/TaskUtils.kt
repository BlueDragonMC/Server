package com.bluedragonmc.server.utils

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.minigame.WinModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.timer.Task
import java.util.function.Predicate

/**
 * Cancels the task when any event of the given [eventType] is triggered in the [game].
 * The received event must pass the [condition] to cancel the task.
 * @return The task, for method chaining
 */
fun <T : Event> Task.cancelOn(game: Game, eventType: Class<out T>, condition: Predicate<T> = Predicate { true }): Task {
    lateinit var module: GameModule
    module = object : GameModule() {
        override fun initialize(parent: Game, eventNode: EventNode<Event>) {
            eventNode.addListener(eventType) { event ->
                if (condition.test(event)) {
                    logger.debug("Canceling task with id ${this@cancelOn.id()} because event of type ${eventType.simpleName} was triggered.")
                    game.unregister(module) // `unregister` triggers `deinitialize`, which cancels the task
                }
            }
        }

        override fun deinitialize() {
            this@cancelOn.cancel()
        }
    }
    game.register(module) { true }
    game.modules.add(module)
    return this
}

/**
 * Cancels the task when the game state is set to ENDING, a winner is declared, or game modules are uninitialized, whichever comes first.
 * @return The task, for method chaining
 */
fun Task.manage(game: Game): Task = cancelOn(game, GameEvent::class.java) { event ->
    when (event) {
        is GameStateChangedEvent -> event.newState == GameState.ENDING
        is WinModule.WinnerDeclaredEvent -> true
        else -> !Game.games.contains(game)
    }
}
