package com.bluedragonmc.server.utils

import net.minestom.server.instance.block.Block

fun Block.isFullCube() = registry().collisionShape().run {
    val start = relativeStart().isZero
    val end = relativeEnd().samePoint(1.0, 1.0, 1.0)
    start && end
}