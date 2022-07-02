package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.utils.SingleAssignmentProperty
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.trait.CancellableEvent
import net.minestom.server.event.trait.PlayerInstanceEvent
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material

/**
 * Assigns a [Menu] to every chest in the world and allows them to be accessed by interacting with the chest.
 * Combine with [ChestLootModule] to add auto-generated loot to chests.
 */
class ChestModule : GameModule() {

    private val menus = mutableMapOf<Point, GuiModule.Menu>()
    private var parent by SingleAssignmentProperty<Game>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {

        require(parent.hasModule<GuiModule>()) { "GuiModule must be present to enable chests." }
        this.parent = parent

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.registry().material() == Material.CHEST) {
                val (inventoryType, pos) = getRootChest(event.blockPosition)
                if (!menus.containsKey(pos)) {
                    val menu = parent.getModule<GuiModule>().createMenu(
                        Component.text(
                            if (inventoryType == InventoryType.CHEST_6_ROW) "Large Chest" else "Chest"
                        ), inventoryType, isPerPlayer = false
                    )
                    MinecraftServer.getGlobalEventHandler().call(
                        ChestPopulateEvent(
                            event.player, event.blockPosition, pos, inventoryType, menu
                        )
                    )
                    menus[pos] = menu
                }
                MinecraftServer.getGlobalEventHandler().callCancellable(
                    ChestOpenEvent(event.player, event.blockPosition, pos, inventoryType, menus[pos]!!)
                ) {
                    menus[pos]!!.open(event.player)
                }
                event.isBlockingItemUse = true
            }
        }
    }

    private fun getRootChest(pos: Point): Pair<InventoryType, Point> {
        val instance = parent.getInstance()
        val nearbyChests = listOf(
            pos.add(1.0, 0.0, 0.0),
            pos.add(-1.0, 0.0, 0.0),
            pos.add(0.0, 0.0, 1.0),
            pos.add(0.0, 0.0, -1.0),
        ).filter { adjacent ->
            instance.getBlock(adjacent).registry().material() == Material.CHEST
        }
        val inventoryType = if (nearbyChests.isNotEmpty()) InventoryType.CHEST_6_ROW else InventoryType.CHEST_3_ROW
        val rootPosition = nearbyChests.filter { menus.containsKey(it) }.sortedBy { it.blockX() }.maxBy { it.blockZ() }
        return inventoryType to rootPosition
    }

    /**
     * Called whenever a chest is opened.
     * If this event is cancelled, the chest's [menu] is not opened for the [player].
     */
    data class ChestOpenEvent(
        private val player: Player,
        val position: Point,
        val baseChestPosition: Point,
        val inventoryType: InventoryType,
        val menu: GuiModule.Menu
    ) : PlayerInstanceEvent, CancellableEvent {
        override fun getPlayer(): Player = player

        private var cancelled = false
        override fun isCancelled() = cancelled

        override fun setCancelled(cancel: Boolean) {
            cancelled = cancel
        }
    }

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
        val menu: GuiModule.Menu
    ) : PlayerInstanceEvent {
        override fun getPlayer(): Player = player
    }
}