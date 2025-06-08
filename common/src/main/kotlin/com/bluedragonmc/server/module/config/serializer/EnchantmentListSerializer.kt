package com.bluedragonmc.server.module.config.serializer

import io.leangen.geantyref.TypeToken
import net.kyori.adventure.key.Key
import net.minestom.server.MinecraftServer
import net.minestom.server.item.component.EnchantmentList
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.registry.RegistryKey
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

class EnchantmentListSerializer : TypeSerializer<EnchantmentList> {
    override fun deserialize(type: Type?, node: ConfigurationNode): EnchantmentList {
        val childrenMap = node.childrenMap()
        val newMap = mutableMapOf<RegistryKey<Enchantment>, Int>()
        for ((key, value) in childrenMap) {
            val registryKey = MinecraftServer.getEnchantmentRegistry().getKey(Key.key(key.toString()))
                ?: error("Unknown enchantment: \"$key\"")
            newMap[registryKey] = value.int
        }
        return EnchantmentList(newMap)
    }

    override fun serialize(type: Type?, obj: EnchantmentList?, node: ConfigurationNode?) {

        val pairs = obj?.enchantments?.entries?.map { entry ->
            entry.key.toString() to entry.value
        } ?: emptyList()

        val map = mapOf(*pairs.toTypedArray())

        node?.set(object : TypeToken<Map<String, Int>>() {}.type, map)
    }
}