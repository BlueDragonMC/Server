package com.bluedragonmc.server.module.config.serializer

import io.leangen.geantyref.TypeToken
import net.minestom.server.item.component.EnchantmentList
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.registry.DynamicRegistry
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

class EnchantmentListSerializer : TypeSerializer<EnchantmentList> {
    override fun deserialize(type: Type?, node: ConfigurationNode): EnchantmentList {
        val childrenMap = node.childrenMap()
        val newMap = mutableMapOf<DynamicRegistry.Key<Enchantment>, Int>()
        for ((key, value) in childrenMap) {
            newMap[DynamicRegistry.Key.of(key.toString())] = value.int
        }
        return EnchantmentList(newMap)
    }

    override fun serialize(type: Type?, obj: EnchantmentList?, node: ConfigurationNode?) {

        val pairs = obj?.enchantments?.entries?.map { entry ->
            entry.key.toString() to entry.value
        } ?: emptyList()

        val map = mapOf(*pairs.toTypedArray())

        node?.set(object: TypeToken<Map<String, Int>>() {}.type, map)
    }
}