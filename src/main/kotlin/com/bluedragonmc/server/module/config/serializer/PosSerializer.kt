package com.bluedragonmc.server.module.config.serializer

import net.minestom.server.coordinate.Pos
import org.spongepowered.configurate.serialize.ScalarSerializer
import java.lang.reflect.Type
import java.util.function.Predicate

class PosSerializer : ScalarSerializer<Pos>(Pos::class.java) {
    override fun deserialize(type: Type?, obj: Any?): Pos {
        val string = obj.toString()
        val split = string.split(",").map { it.trim().toDouble() }
        return when (split.size) {
            3 -> Pos(split[0], split[1], split[2])
            5 -> Pos(split[0], split[1], split[2], split[3].toFloat(), split[4].toFloat())
            else -> error("Invalid number of elements: ${split.size}: expected 3 or 5")
        }
    }

    override fun serialize(item: Pos?, typeSupported: Predicate<Class<*>>?): Any {
        return item?.run { "$x,$y,$z,$yaw,$pitch" } ?: error("Cannot serialize null Pos")
    }
}