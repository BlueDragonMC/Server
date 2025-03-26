package com.bluedragonmc.server.module.config.serializer

import net.minestom.server.entity.EntityType
import org.spongepowered.configurate.serialize.ScalarSerializer
import java.lang.reflect.Type
import java.util.function.Predicate

class EntityTypeSerializer  : ScalarSerializer<EntityType>(EntityType::class.java) {
    override fun deserialize(type: Type?, obj: Any?): EntityType {
        val string = obj.toString()
        return EntityType.fromKey(string)
    }

    override fun serialize(item: EntityType?, typeSupported: Predicate<Class<*>>?): Any? {
        return item?.key()?.asString()
    }
}