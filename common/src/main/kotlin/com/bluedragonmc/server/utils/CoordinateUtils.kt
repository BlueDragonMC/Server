package com.bluedragonmc.server.utils

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import kotlin.math.abs
import kotlin.math.min

operator fun Point.component1() = x()
operator fun Point.component2() = y()
operator fun Point.component3() = z()

operator fun Pos.component4() = yaw
operator fun Pos.component5() = pitch

object CoordinateUtils {
    fun getAllInBox(pos1: Pos, pos2: Pos): List<Pos> {
        val dx = abs(pos2.blockX() - pos1.blockX())
        val dy = abs(pos2.blockY() - pos1.blockY())
        val dz = abs(pos2.blockZ() - pos1.blockZ())
        val minX = min(pos1.blockX(), pos2.blockX())
        val minY = min(pos1.blockY(), pos2.blockY())
        val minZ = min(pos1.blockZ(), pos2.blockZ())
        return (0 .. dx).map { x ->
            (0 .. dy).map { y ->
                (0 .. dz).map { z ->
                    Pos(x.toDouble() + minX, y.toDouble() + minY, z.toDouble() + minZ)
                }
            }
        }.flatten().flatten()
    }
}