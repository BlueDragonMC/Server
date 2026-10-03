package com.bluedragonmc.server.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import kotlin.coroutines.CoroutineContext

/**
 * A [CoroutineDispatcher] that runs coroutine continuations on the server's tick thread.
 * This dispatcher schedules work to run at the start of the next server tick.
 */
object TickDispatcher : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val scheduler = MinecraftServer.getSchedulerManager()
        if (scheduler == null) {
            // Server not running (maybe we're in a unit test). Run inline so the coroutine still makes progress.
            block.run()
        } else {
            scheduler.scheduleNextTick(block)
        }
    }

    private val scope = CoroutineScope(TickDispatcher + SupervisorJob())

    /**
     * Launches [block] on the tick thread. The returned [Job] can be used to cancel the work.
     */
    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}
