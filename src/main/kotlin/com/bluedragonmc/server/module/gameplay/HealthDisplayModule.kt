package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.withTransition
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent

/**
 * Displays the player's health as a percentage of their max health in the action bar.
 */
class HealthDisplayModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerTickEvent::class.java) { event ->
            event.entity.sendActionBar(
                Component.text("Health: ", BRAND_COLOR_PRIMARY_2) +
                        Component.text(getHealthPercent(event.entity)).withTransition(
                            event.entity.health / event.entity.maxHealth,
                            NamedTextColor.RED,
                            NamedTextColor.YELLOW,
                            NamedTextColor.GREEN
                        )
            )
        }
    }

    private fun getHealthPercent(player: Player) = String.format("%.1f%%", player.health / player.maxHealth * 100)

}