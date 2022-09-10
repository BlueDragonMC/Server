package com.bluedragonmc.games.fastfall

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.Generator
import kotlin.math.pow
import kotlin.math.sqrt

class ChaosWorldGenerator(private val radius: Int, private val blockSet: List<Block>) : Generator {
    override fun generate(unit: GenerationUnit) {
        val start = unit.absoluteStart()
        val end = unit.absoluteEnd()
        unit.fork { setter ->
            for (x in start.x().toInt() until end.x().toInt()) {
                for (y in start.y().toInt() until end.y().toInt()) {
                    for (z in start.z().toInt() until end.z().toInt()) {

                        fun inCircle(radius: Int) = pointInCircle(x.toDouble(), z.toDouble(), 0, 0, radius)

                        if (y == 256 - 2 * radius && inCircle(10))
                            setter.setBlock(x, y, z, Block.EMERALD_BLOCK) // Win point
                        else if (y == 256 - 2 * radius + 1 && inCircle(10))
                            setter.setBlock(x, y, z, Block.GLASS) // Breakable glass over win point
                        else if ((x == -5 || x == 5) && y == 256 && (z == -5 || z == 5))
                            setter.setBlock(x, y, z, Block.BEDROCK) // Spawn point
                        else if (!pointInSphere(Vec(x.toDouble(), y.toDouble(), z.toDouble()), radius))
                            continue
                        else if ((1..20).random() == 1)
                            setter.setBlock(x, y, z, if ((1..23).random() == 1) Block.SLIME_BLOCK else randomBlock())
                    }
                }
            }
        }
    }

    private fun randomBlock(): Block {
        return blockSet.random()
    }

    /**
     * Checks if the point is in the sphere with specified radius centered at 0, 256-radius, 0.
     */
    private fun pointInSphere(point: Point, radius: Int): Boolean {
        val distance = sqrt(point.x().pow(2) + (point.y() - (256 - radius)).pow(2) + point.z().pow(2))
        return distance <= radius
    }

    /**
     * Checks if the point (pointX, pointY) is inside the circle with center (centerX, centerY).
     */
    private fun pointInCircle(pointX: Double, pointY: Double, centerX: Int, centerY: Int, radius: Int): Boolean {
        val distance = sqrt((pointX - centerX).pow(2) + (pointY - centerY).pow(2))
        return distance <= radius
    }

}
