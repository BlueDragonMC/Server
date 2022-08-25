package com.bluedragonmc.server.module.config.serializer

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.spongepowered.configurate.serialize.ScalarSerializer
import java.lang.reflect.Type
import java.util.function.Predicate

class ComponentSerializer : ScalarSerializer<Component>(Component::class.java) {
    override fun deserialize(type: Type?, obj: Any?): Component {
        val string = obj.toString()
        return MiniMessage.miniMessage().deserialize(string)
    }

    override fun serialize(item: Component?, typeSupported: Predicate<Class<*>>?): Any {
        return MiniMessage.miniMessage().serialize(item ?: Component.empty())
    }
}