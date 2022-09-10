package com.bluedragonmc.server.event

import com.bluedragonmc.server.module.GuiModule
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerInstanceEvent
import net.minestom.server.inventory.InventoryType

/**
 * Called when a chest is first populated.
 * This event is not per-player, it is only called when a chest's [menu] is created.
 * The player in this event is always the first [player] to open the chest at [baseChestPosition].
 */
data class ChestPopulateEvent(
    private val player: Player,
    val position: Point,
    val baseChestPosition: Point,
    val inventoryType: InventoryType,
    val menu: GuiModule.Menu,
) : PlayerInstanceEvent {
    override fun getPlayer(): Player = player
}