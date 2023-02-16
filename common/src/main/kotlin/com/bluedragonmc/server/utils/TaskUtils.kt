package com.bluedragonmc.server.utils

import com.bluedragonmc.server.Game
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
                    this@cancelOn.cancel()
                    game.unregister(module)
                }
            }
        }
    }
    game.register(module) { true }
    game.modules.add(module)
    return this
}

/**
 * Cancels the task when a [WinModule.WinnerDeclaredEvent] is received.
 * @return The task, for method chaining
 */
fun Task.manage(game: Game): Task = cancelOn(game, WinModule.WinnerDeclaredEvent::class.java)