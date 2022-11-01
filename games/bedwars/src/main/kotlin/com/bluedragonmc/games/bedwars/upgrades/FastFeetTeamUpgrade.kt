package com.bluedragonmc.games.bedwars.upgrades

import net.kyori.adventure.text.Component
import net.minestom.server.attribute.Attribute
import net.minestom.server.attribute.AttributeModifier
import net.minestom.server.attribute.AttributeOperation
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

class FastFeetTeamUpgrade : TeamUpgrade(
    Component.translatable("game.bedwars.upgrade.fast_feet.name"),
    Component.translatable("game.bedwars.upgrade.fast_feet.desc"),
    Material.IRON_BOOTS
) {
    override fun onObtained(player: Player) {
        player.getAttribute(Attribute.MOVEMENT_SPEED).addModifier(SPEED_MODIFIER)
    }

    companion object {
        private val SPEED_MODIFIER = AttributeModifier("bluedragon:fastfeet", 0.4f, AttributeOperation.MULTIPLY_BASE)
    }
}