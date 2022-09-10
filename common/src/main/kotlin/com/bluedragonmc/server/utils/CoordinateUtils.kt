package com.bluedragonmc.server.utils

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos

operator fun Point.component1() = x()
operator fun Point.component2() = y()
operator fun Point.component3() = z()

operator fun Pos.component4() = yaw
operator fun Pos.component5() = pitch