package com.bluedragonmc.server.utils

import java.time.Duration

fun formatDuration(millis: Long, milliseconds: Boolean = true): String =
    formatDuration(Duration.ofMillis(millis), milliseconds)

fun formatDuration(duration: Duration, milliseconds: Boolean = true): String {
    return if (milliseconds) {
        String.format("%02d:%02d:%02d.%03d",
            duration.toHoursPart(),
            duration.toMinutesPart(),
            duration.toSecondsPart(),
            duration.toMillisPart())
    } else {
        String.format("%02d:%02d:%02d",
            duration.toHoursPart(),
            duration.toMinutesPart(),
            duration.toSecondsPart())
    }
}