package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerSwapItemEvent
import net.minestom.server.inventory.PlayerInventory

/**
 * A module to manage the player's ability to edit their inventory.
 *
 * [See Documentation](https://developer.bluedragonmc.com/modules/inventorypermissionsmodule/)
 *
 * @property allowDropItem If false, players will not be able to move items out of their inventories (e.g. by pressing Q on an item).
 * @property allowMoveItem If false, players will not be allowed to move items within their inventories.
 */
class InventoryPermissionsModule(var allowDropItem: Boolean, var allowMoveItem: Boolean) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(InventoryPreClickEvent::class.java) { event ->
            if (event.inventory !is PlayerInventory) return@addListener
            event.isCancelled = !allowMoveItem
        }
        eventNode.addListener(ItemDropEvent::class.java) { event ->
            event.isCancelled = !allowDropItem
        }
        eventNode.addListener(PlayerSwapItemEvent::class.java) { event ->
            event.isCancelled = !allowMoveItem
        }
    }
}