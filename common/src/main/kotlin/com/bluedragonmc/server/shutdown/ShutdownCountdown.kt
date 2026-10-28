package com.bluedragonmc.server.shutdown

import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Keeps track of a scheduled server shutdown time and exposes messages
 * to send to players (in the chat and in the sidebar) to inform them.
 */
class ShutdownCountdown(
    private val totalSeconds: Long,
    private val nanoTime: () -> Long = System::nanoTime,
    private val onTick: (remainingSeconds: Long) -> Unit = {},
    private val onMilestone: (remainingSeconds: Long) -> Unit = {},
    private val onComplete: () -> Unit = {},
) {

    private var startNanos = 0L
    private var lastRemaining = totalSeconds
    private var started = false
    private var completed = false

    init {
        require(totalSeconds > 0) { "The shutdown countdown must be longer than zero seconds." }
    }

    /**
     * Starts the countdown, immediately reporting the full remaining time.
     */
    fun start() {
        check(!started) { "The shutdown countdown has already been started." }
        started = true
        startNanos = nanoTime()
        lastRemaining = totalSeconds
        onTick(totalSeconds)
        onMilestone(totalSeconds)
    }

    /**
     * Advances the countdown. This should be called at least once per second.
     */
    fun tick() {
        check(started) { "The shutdown countdown has not been started." }
        if (completed) return

        val remaining = remainingSeconds()
        if (remaining < lastRemaining) {
            onTick(remaining)

            MILESTONE_SECONDS.lastOrNull { it in remaining..<lastRemaining }
                ?.let(onMilestone)

            lastRemaining = remaining
        }

        if (remaining <= 0) {
            completed = true
            onComplete()
        }
    }

    fun remainingSeconds(): Long {
        val nanosLeft = deadlineNanos() - nanoTime()
        return if (nanosLeft <= 0) 0 else (nanosLeft + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND
    }

    private fun deadlineNanos() = startNanos + TimeUnit.SECONDS.toNanos(totalSeconds)

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L

        /**
         * Player-facing milestones, in seconds remaining.
         */
        val MILESTONE_SECONDS: List<Long> = listOf(
            2 * 60 * 60L, // 2 hours
            60 * 60L, // 1 hour
            30 * 60L, // 30 minutes
            20 * 60L, // 20 minutes
            10 * 60L, // 10 minutes
            5 * 60L, // 5 minutes
            4 * 60L, // 4 minutes
            3 * 60L, // 3 minutes
            2 * 60L, // 2 minutes
            60L, // 1 minute
            30L, // 30 seconds
            20L, // 20 seconds
            10L, // 10 seconds
            5L, // 5 seconds
            4L, // 4 seconds
            3L, // 3 seconds
            2L, // 2 seconds
            1L, // 1 second
        )

        /**
         * Formats a duration for a chat message, for example "30 minutes" or "1 hour".
         */
        fun formatChat(seconds: Long): String {
            require(seconds >= 0) { "Cannot format a negative duration." }
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val remainingSeconds = seconds % 60

            return when {
                hours > 0 && minutes == 0L && remainingSeconds == 0L -> plural(hours, "hour")
                hours > 0 -> "${plural(hours, "hour")} ${plural(minutes, "minute")}"
                minutes > 0 && remainingSeconds == 0L -> plural(minutes, "minute")
                minutes > 0 -> "${plural(minutes, "minute")} ${plural(remainingSeconds, "second")}"
                else -> plural(remainingSeconds, "second")
            }
        }

        /**
         * Formats a duration for the sidebar, for example "2:00:00" or "0:30".
         */
        fun formatSidebar(seconds: Long): String {
            require(seconds >= 0) { "Cannot format a negative duration." }
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val remainingSeconds = seconds % 60
            return if (hours > 0) {
                String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds)
            } else {
                String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds)
            }
        }

        private fun plural(amount: Long, unit: String) = if (amount == 1L) "1 $unit" else "$amount ${unit}s"
    }
}
