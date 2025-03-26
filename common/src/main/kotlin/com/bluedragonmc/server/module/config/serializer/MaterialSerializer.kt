package com.bluedragonmc.server.module.config.serializer

import net.minestom.server.item.Material
import org.spongepowered.configurate.serialize.ScalarSerializer
import java.lang.reflect.Type
import java.util.function.Predicate

class MaterialSerializer : ScalarSerializer<Material>(Material::class.java) {
    override fun deserialize(type: Type?, obj: Any?): Material? {
        val string = obj.toString()
        return Material.fromKey(string)
    }

    override fun serialize(item: Material?, typeSupported: Predicate<Class<*>>?): Any? {
        return item?.key()?.asString()
    }
}
