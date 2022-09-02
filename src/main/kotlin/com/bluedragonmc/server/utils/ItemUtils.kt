package com.bluedragonmc.server.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.color.Color
import net.minestom.server.entity.Player
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.metadata.LeatherArmorMeta

object ItemUtils {
    fun knockbackStick(kbLevel: Short, player: Player): ItemStack =
        ItemStack.builder(Material.STICK).displayName(Component.translatable("global.items.kb_stick.name"))
            .lore(splitAndFormatLore(Component.translatable("global.items.kb_stick.lore"), NamedTextColor.GRAY, player))
            .meta { metaBuilder: ItemMeta.Builder ->
                metaBuilder.enchantment(Enchantment.KNOCKBACK, kbLevel)
            }.build()
    fun ItemStack.withEnchant(enchantment: Enchantment, level: Short): ItemStack = withMeta { metaBuilder: ItemMeta.Builder -> metaBuilder.enchantment(enchantment, level) }

    fun ItemStack.withArmorColor(color: Color): ItemStack {
        return withMeta(LeatherArmorMeta::class.java) { builder -> builder.color(color) }
    }
}