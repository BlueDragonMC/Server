package com.bluedragonmc.server.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.color.Color
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.DyedItemColor
import net.minestom.server.item.component.EnchantmentList
import net.minestom.server.item.enchant.Enchantment

object ItemUtils {
    fun knockbackStick(kbLevel: Int, player: Player): ItemStack =
        ItemStack.builder(Material.STICK).set(ItemComponent.ITEM_NAME, Component.translatable("global.items.kb_stick.name"))
            .set(ItemComponent.LORE, splitAndFormatLore(Component.translatable("global.items.kb_stick.lore"), NamedTextColor.GRAY, player))
            .set(ItemComponent.ENCHANTMENTS, EnchantmentList(Enchantment.KNOCKBACK, kbLevel))
            .build()

    fun ItemStack.withArmorColor(color: Color): ItemStack {
        return with(ItemComponent.DYED_COLOR, DyedItemColor(color))
    }
}