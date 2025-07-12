package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.PlayerJoinGameEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

/**
 * Sets the max health of the player when they join the game.
 * This sets the base value of the attribute, it does not add a modifier.
 * The module automatically resets their max health to 20 when they leave the instance.
 */
class MaxHealthModule(private val maxHealth: Double) : GameModule() {

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerJoinGameEvent::class.java) { event ->
            event.player.getAttribute(Attribute.MAX_HEALTH).baseValue = maxHealth
        }
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            event.player.getAttribute(Attribute.MAX_HEALTH).baseValue = 20.0
        }
    }
}