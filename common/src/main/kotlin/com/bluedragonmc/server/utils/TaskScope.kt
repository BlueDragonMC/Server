package com.bluedragonmc.server.utils

import net.minestom.server.MinecraftServer
import net.minestom.server.timer.ExecutionType
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.time.temporal.TemporalUnit
import java.util.*

class TaskScope internal constructor() {

    private val tasks: MutableSet<Task> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    internal fun buildTask(block: Runnable): ScopedTaskBuilder = ScopedTaskBuilder(this, block)

    fun scheduleNextTick(block: Runnable): Task =
        MinecraftServer.getSchedulerManager().scheduleNextTick(block).also(::track)

    internal fun track(task: Task) {
        tasks.add(task)
    }

    /**
     * Cancels every task that is still alive.
     */
    fun cancelAll() {
        synchronized(tasks) {
            for (task in tasks) task.cancel()
        }
        tasks.clear()
    }
}

/**
 * A wrapper around Minestom's `Task.Builder` that registers the resulting [Task]
 * with a [TaskScope] when [schedule] is called.
 */
class ScopedTaskBuilder internal constructor(
    private val scope: TaskScope,
    private val block: Runnable,
) {
    private var executionType: ExecutionType = ExecutionType.TICK_START
    private var delay: TaskSchedule? = null
    private var repeat: TaskSchedule? = null

    fun executionType(executionType: ExecutionType) = apply { this.executionType = executionType }

    fun delay(schedule: TaskSchedule) = apply { this.delay = schedule }
    fun delay(duration: Duration) = delay(TaskSchedule.duration(duration))
    fun delay(time: Long, unit: TemporalUnit) = delay(TaskSchedule.duration(time, unit))

    fun repeat(schedule: TaskSchedule) = apply { this.repeat = schedule }
    fun repeat(duration: Duration) = repeat(TaskSchedule.duration(duration))
    fun repeat(time: Long, unit: TemporalUnit) = repeat(TaskSchedule.duration(time, unit))

    fun schedule(): Task {
        var builder = MinecraftServer.getSchedulerManager().buildTask(block).executionType(executionType)
        delay?.let { builder = builder.delay(it) }
        repeat?.let { builder = builder.repeat(it) }
        return builder.schedule().also { scope.track(it) }
    }
}

/**
 * Implemented by anything that can own scheduled tasks, such as `ModuleHolder` and `GameModule`.
 * Tasks scheduled through this interface are automatically cancelled when the module is
 * unregistered or the game ends.
 */
interface TaskScheduler {
    val taskScope: TaskScope

    fun buildTask(block: Runnable): ScopedTaskBuilder = taskScope.buildTask(block)

    fun scheduleNextTick(block: Runnable): Task = taskScope.scheduleNextTick(block)
}
