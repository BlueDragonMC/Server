package com.bluedragonmc.server.utils

import java.util.*
import java.util.concurrent.ThreadLocalRandom

/**
 * Generates a version 7 (time-ordered) UUID as described by RFC 9562.
 */
fun uuidV7(): UUID {
    val timestamp = System.currentTimeMillis() and 0xFFFF_FFFF_FFFFL
    val random = ThreadLocalRandom.current()

    val mostSignificantBits = (timestamp shl 16) or 0x7000L or random.nextLong(0x1000L)
    val leastSignificantBits = (random.nextLong() and 0x3FFF_FFFF_FFFF_FFFFL) or Long.MIN_VALUE

    return UUID(mostSignificantBits, leastSignificantBits)
}
