package com.bluedragonmc.server.module.config.serializer

import com.bluedragonmc.server.utils.noItalic
import net.kyori.adventure.text.Component
import net.minestom.server.color.Color
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.DyedItemColor
import net.minestom.server.item.component.EnchantmentList
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

class ItemStackSerializer : TypeSerializer<ItemStack> {

    override fun deserialize(type: Type?, node: ConfigurationNode): ItemStack {
        val material = node.node("material").get<Material>() ?: error("No material present")
        val amount = node.node("amount").getInt(1)
        println(material.registry().namespace())
        println(node.node("enchants").toString())
        node.hasChild("enchants")

        val enchantments = node.node("enchants").get<EnchantmentList>()

        val name = node.node("name").get<Component>()?.noItalic()
        val lore = node.node("lore").getList(Component::class.java)
        val dye = node.node("dye").get<Color>()

        val itemStack = ItemStack.builder(material).run {

            amount(amount)

            if (name != null) {
                set(ItemComponent.ITEM_NAME, name)
            }

            if (lore != null) {
                set(ItemComponent.LORE, lore)
            }

            set(ItemComponent.ENCHANTMENTS, enchantments)

            build()
        }

        if (dye != null) {
            return itemStack.with(ItemComponent.DYED_COLOR, DyedItemColor(dye))
        }

        return itemStack
    }

    override fun serialize(type: Type?, obj: ItemStack?, node: ConfigurationNode?) {
        node?.node("material")?.set(obj?.material())
        node?.node("amount")?.set(obj?.amount())
        node?.node("enchants")?.set(obj?.get(ItemComponent.ENCHANTMENTS)?.enchantments)
        node?.node("name")?.set(obj?.get(ItemComponent.ITEM_NAME))
        node?.node("lore")?.set(obj?.get(ItemComponent.LORE))
    }
}