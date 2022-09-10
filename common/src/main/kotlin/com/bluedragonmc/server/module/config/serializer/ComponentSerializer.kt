package com.bluedragonmc.server.module.config.serializer

import com.bluedragonmc.server.utils.miniMessage
import net.kyori.adventure.text.Component
import org.spongepowered.configurate.serialize.ScalarSerializer
import java.lang.reflect.Type
import java.util.function.Predicate

class ComponentSerializer : ScalarSerializer<Component>(Component::class.java) {
    override fun deserialize(type: Type?, obj: Any?): Component {
        val string = obj.toString()
        return miniMessage.deserialize(string)
    }

    override fun serialize(item: Component?, typeSupported: Predicate<Class<*>>?): Any {
        return miniMessage.serialize(item ?: Component.empty())
    }
}