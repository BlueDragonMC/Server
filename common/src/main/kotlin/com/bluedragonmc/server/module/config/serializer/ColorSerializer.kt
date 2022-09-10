package com.bluedragonmc.server.module.config.serializer

import net.minestom.server.color.Color
import org.spongepowered.configurate.serialize.ScalarSerializer
import java.lang.reflect.Type
import java.util.function.Predicate

class ColorSerializer : ScalarSerializer<Color>(Color::class.java) {
    override fun deserialize(type: Type?, obj: Any?): Color {
        val string = obj.toString()
        val split = string.split(",").map { it.trim().toInt() }
        return when (split.size) {
            3 -> Color(split[0], split[1], split[2])
            else -> error("Invalid number of elements: ${split.size}: expected 3")
        }
    }

    override fun serialize(item: Color?, typeSupported: Predicate<Class<*>>?): Any {
        return item?.run { "${red()},${green()},${blue()}" } ?: error("Cannot serialize null Color")
    }
}