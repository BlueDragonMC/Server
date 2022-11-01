package com.bluedragonmc.games.bedwars.upgrades

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.gameplay.ShopModule
import com.bluedragonmc.server.module.minigame.TeamModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

abstract class TeamUpgrade(
    private val name: Component,
    description: Component,
    displayItem: Material
) {

    val virtualItem = ShopModule.VirtualItem(name, description, displayItem, ::handleObtained)

    private fun handleObtained(player: Player, item: ShopModule.VirtualItem) {
        val team = Game.findGame(player)?.getModule<TeamModule>()?.getTeam(player)
        team?.players?.forEach {
            onObtained(player)
            (it as CustomPlayer).virtualItems.add(item)
        }
        team?.sendMessage(
            Component.translatable("module.shop.team_upgrade.purchased", NamedTextColor.GREEN,
                player.name, name
            )
        )
    }

    abstract fun onObtained(player: Player)
}