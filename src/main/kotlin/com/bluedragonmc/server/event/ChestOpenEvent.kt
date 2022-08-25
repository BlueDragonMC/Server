package com.bluedragonmc.server.event

import com.bluedragonmc.server.module.GuiModule
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerInstanceEvent
import net.minestom.server.inventory.InventoryType

/**
 * Called whenever a chest is opened.
 * If this event is cancelled, the chest's [menu] is not opened for the [player].
 */
data class ChestOpenEvent(
    private val player: Player,
    val position: Point,
    val baseChestPosition: Point,
    val inventoryType: InventoryType,
    val menu: GuiModule.Menu,
) : PlayerInstanceEvent, Cancellable() {
    override fun getPlayer(): Player = player
}