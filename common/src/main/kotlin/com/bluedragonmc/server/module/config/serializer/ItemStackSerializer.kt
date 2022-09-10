package com.bluedragonmc.server.module.config.serializer

import com.bluedragonmc.server.utils.noItalic
import com.google.gson.reflect.TypeToken
import net.kyori.adventure.text.Component
import net.minestom.server.color.Color
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.metadata.LeatherArmorMeta
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

class ItemStackSerializer : TypeSerializer<ItemStack> {

    private val enchType =
        TypeToken.getParameterized(Map::class.java, Enchantment::class.java, Short::class.javaObjectType).type

    override fun deserialize(type: Type?, node: ConfigurationNode): ItemStack {
        val material = node.node("material").get<Material>() ?: error("No material present")
        val amount = node.node("amount").getInt(1)
        @Suppress("UNCHECKED_CAST") val enchantments = node.node("enchants").get(enchType) as Map<Enchantment, Short>?
        val name = node.node("name").get<Component>()?.noItalic()
        val lore = node.node("lore").getList(Component::class.java)
        val dye = node.node("dye").get<Color>()

        val itemStack = ItemStack.builder(material).run {

            amount(amount)

            meta { builder ->
                if (enchantments != null && enchantments.isNotEmpty()) builder.enchantments(enchantments)
                if (name != null) builder.displayName(name)
                if (lore != null) builder.lore(*lore.toTypedArray())
            }
            build()
        }

        if (dye != null) return itemStack.withMeta(LeatherArmorMeta::class.java) { it.color(dye) }

        return itemStack
    }

    override fun serialize(type: Type?, obj: ItemStack?, node: ConfigurationNode?) {
        node?.node("material")?.set(obj?.material())
        node?.node("amount")?.set(obj?.amount())
        node?.node("enchants")?.set(obj?.meta()?.enchantmentMap)
        node?.node("name")?.set(obj?.displayName)
        node?.node("lore")?.set(obj?.lore)
    }
}