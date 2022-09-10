package com.bluedragonmc.server.module.vanilla

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.entity.GameMode
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
            val entity = event.entity
            if (entity !is Player) return@addListener
            if(entity.gameMode == GameMode.SPECTATOR) {
                event.isCancelled = true
                return@addListener
            }
            entity.inventory.addItemStack(event.itemStack)
        }
    }
}