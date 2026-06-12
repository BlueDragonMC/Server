package com.bluedragonmc.server.module.config.serializer

import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.AnnotatedType

class PointSerializer : TypeSerializer.Annotated<Point> {
    override fun deserialize(
        type: AnnotatedType?,
        node: ConfigurationNode?
    ): Point? {
        if (node == null) return null
        val string = node.raw().toString()
        val split = string.split(",").map { it.trim().toDouble() }
        return when (split.size) {
            3 -> Pos(split[0], split[1], split[2])
            5 -> Pos(split[0], split[1], split[2], split[3].toFloat(), split[4].toFloat())
            else -> error("Invalid number of elements: ${split.size}: expected 3 or 5")
        }
    }

    override fun serialize(
        type: AnnotatedType?,
        item: Point?,
        node: ConfigurationNode
    ) {
        val stringValue = when (item) {
            is Pos -> item.run { "$x,$y,$z,$yaw,$pitch" }
            is Vec -> item.run { "$x,$y,$z" }
            is BlockVec -> item.run { "$blockX,$blockY,$blockZ" }
            null -> error("Cannot serialize null Point")
        }

        node.raw(stringValue)
    }
}
