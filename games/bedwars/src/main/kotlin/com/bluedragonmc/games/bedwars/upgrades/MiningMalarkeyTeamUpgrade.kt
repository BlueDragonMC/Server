package com.bluedragonmc.games.bedwars.upgrades

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerRespawnEvent
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect

class MiningMalarkeyTeamUpgrade : TeamUpgrade(
    Component.translatable("game.bedwars.upgrade.mining_malarkey.name"),
    Component.translatable("game.bedwars.upgrade.mining_malarkey.desc"),
    Material.IRON_PICKAXE
) {

    override fun onObtained(player: Player) {
        player.addEffect(POTION_EFFECT)
    }

    init {
        virtualItem.eventNode.addListener(PlayerRespawnEvent::class.java) { event ->
            event.player.addEffect(POTION_EFFECT)
        }
    }

    companion object {
        private val POTION_EFFECT = Potion(PotionEffect.HASTE, 1, Integer.MAX_VALUE, Potion.ICON_FLAG)
    }

}