package com.bluedragonmc.server.module.config.serializer

import net.minestom.server.item.enchant.Enchantment
import org.spongepowered.configurate.serialize.ScalarSerializer
import java.lang.reflect.Type
import java.util.function.Predicate

class EnchantmentSerializer : ScalarSerializer<Enchantment>(Enchantment::class.java) {
    override fun deserialize(type: Type?, obj: Any?): Enchantment? {
        val string = obj.toString()
        return Enchantment.fromNamespaceId(string)
    }

    override fun serialize(item: Enchantment?, typeSupported: Predicate<Class<*>>?): Any? {
        return item?.namespace()?.asString()
    }
}