package com.bluedragonmc.server.utils

import kotlinx.coroutines.*
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.util.function.Consumer
import kotlin.coroutines.CoroutineContext

/**
 * Shortcut for [EventNode.addListener] using a reified type parameter.
 */
inline fun <reified T : Event> EventNode<in T>.listen(consumer: Consumer<T>) {
    this.addListener(T::class.java, consumer)
}

/**
 * Listens to an event and runs a suspending handler in a
 * blocking context. This will prevent other handlers from
 * executing, so this should only be used when necessary.
 */
inline fun <reified T : Event> EventNode<in T>.listenSuspend(crossinline consumer: suspend (T) -> Unit) {
    this.addListener(T::class.java) { event ->
        runBlocking {
            consumer.invoke(event)
        }
    }
}

/**
 * Listens to an event and runs a handler in a coroutine.
 * Modifications and cancellations to the event will do nothing.
 */
inline fun <reified T : Event> EventNode<in T>.listenAsync(crossinline consumer: suspend (T) -> Unit) {
    this.addListener(T::class.java) { event ->
        asyncEventCoroutineScope.launch {
            consumer.invoke(event)
        }
    }
}

/**
 * A [CoroutineScope] which should only be used
 * for executing 'async' event handlers.
 */
val asyncEventCoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        Dispatchers.IO + SupervisorJob() + CoroutineName("Async Event Handling")
}