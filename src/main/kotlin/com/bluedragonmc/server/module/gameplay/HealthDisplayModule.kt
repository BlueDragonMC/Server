package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component
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
                        Component.text(getHealthPercent(event.entity), BRAND_COLOR_PRIMARY_1)
            )
        }
    }

    fun getHealthPercent(player: Player): String {
        return (player.health / player.maxHealth * 100).toString() + "%"
    }

}