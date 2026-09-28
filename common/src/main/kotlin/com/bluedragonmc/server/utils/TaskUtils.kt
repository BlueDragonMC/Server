package com.bluedragonmc.server.utils

import com.bluedragonmc.server.ModuleHolder
import net.minestom.server.event.Event
import net.minestom.server.event.EventListener
import net.minestom.server.timer.Task
import java.util.function.Predicate

/**
 * Cancels the task when any event of the given [eventType] is triggered in the [holder].
 * The received event must pass the [condition] to cancel the task.
 * @return The task, for method chaining
 */
fun <T : Event> Task.cancelOn(holder: ModuleHolder, eventType: Class<T>, condition: Predicate<T> = Predicate { true }): Task {
    lateinit var listener: EventListener<T>
    listener = EventListener.of(eventType) { event ->
        if (condition.test(event)) {
            holder.rootEventNode.removeListener(listener)
            this.cancel()
        }
    }
    holder.rootEventNode.addListener(listener)
    return this
}
