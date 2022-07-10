package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.PickupItemEvent

/**
 * Allows players to pick up items.
 */
class ItemPickupModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PickupItemEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener
            (event.entity as Player).inventory.addItemStack(event.itemStack)
        }
    }
}