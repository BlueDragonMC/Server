package com.bluedragonmc.server.module.vanilla

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.PickItemEvent
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.trait.PlayerInstanceEvent
import net.minestom.server.instance.block.Block

/**
 * Enables the vanilla "pick block" functionality (middle click) for survival mode
 */
class PickItemModule : GameModule() {
    override fun initialize(
        parent: Game,
        eventNode: EventNode<Event>
    ) {
        eventNode.addListener(PickItemEvent.Block::class.java) { event ->
            val block = event.instance.getBlock(event.pos)
            if (event.player.inventory.getItemStack(event.player.heldSlot.toInt()).material().block()?.compare(block, Block.Comparator.ID) == true) {
                // If the player is already holding a matching block, do nothing
                return@addListener
            }
            for (slot in 0..8) {
                if (event.player.inventory.getItemStack(slot).material().block()?.compare(block, Block.Comparator.ID) == true) {
                    event.player.setHeldItemSlot(slot.toByte())
                    return@addListener
                }
            }
        }
    }
}