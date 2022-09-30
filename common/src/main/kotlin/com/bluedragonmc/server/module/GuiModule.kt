package com.bluedragonmc.server.module

import com.bluedragonmc.server.Game
import net.kyori.adventure.text.Component
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.inventory.click.ClickType
import net.minestom.server.inventory.condition.InventoryCondition
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

/**
 * A library for creating GUIs with buttons that react to user input.
 * Code example:
 * ```
 * val menu = createMenu(Component.text("WackyMaze Shop"), InventoryType.CHEST_6_ROW, isPerPlayer = true) {
 * // This block is a builder for the menu's slots. Use the `slot` method to create a new slot, and it is immediately added to the menu.
 *   slot(pos(6, 5), Material.BARRIER, { player -> // Inventories are generated per-player by default, so items can be customized depending on whose inventory is being viewed.
 *     // This block's context is Minestom's `ItemStack.Builder`, so all of its methods can be used without method chaining or running `build()`
 *     displayName(Component.text("Close", NamedTextColor.RED))
 *     lore(player.displayName)
 *   }) {
 *     // The second block passed to the `slot` method is the action that will be triggered when the slot is clicked. It receives a `SlotClickEvent`.
 *     menu.close(player)
 *   } // More slots can be registered exactly the same way using the `slot` method.
 * }
 * ```
 */
open class GuiModule : GameModule() {

    companion object {
        internal val inventories = mutableMapOf<Byte, Menu>()
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(InventoryCloseEvent::class.java) { event ->
            inventories[event.inventory?.windowId]?.let {
                it.destroy(event.player)
                it.onClosedAction?.invoke(event.player)
            }
        }
        eventNode.addListener(PlayerTickEvent::class.java) { event ->
            val openInv = event.player.openInventory?.windowId ?: return@addListener
            inventories[openInv]?.let {
                it.onTickAction?.invoke(event.player)
            }
        }
    }

    fun createMenu(
        title: Component,
        inventoryType: InventoryType,
        isPerPlayer: Boolean = true,
        allowSpectatorClicks: Boolean = false,
        items: ItemsBuilder.() -> Unit = {},
    ): Menu {
        val builder = ItemsBuilder(inventoryType)
        items(builder)
        return Menu(title, inventoryType, builder.build(), isPerPlayer, allowSpectatorClicks)
    }

    data class Menu(
        val title: Component,
        val inventoryType: InventoryType,
        private val items: List<Slot>,
        private val isPerPlayer: Boolean,
        private val allowSpectatorClicks: Boolean,
    ) {

        private lateinit var cachedInventory: Inventory
        private var cachedInventories = mutableMapOf<Player, Inventory>()

        private var onOpenedAction: ((Player) -> Unit)? = null
        internal var onTickAction: ((Player) -> Unit)? = null
        internal var onClosedAction: ((Player) -> Unit)? = null

        private fun getInventory(player: Player): Inventory {
            if (!isPerPlayer && this::cachedInventory.isInitialized) return cachedInventory
            if (isPerPlayer && cachedInventories.containsKey(player)) return cachedInventories[player]!!
            return Inventory(inventoryType, title).apply {
                items.forEach { item ->
                    setItemStack(item.index, item.itemStackBuilder(ItemStack.builder(item.material), player).build())
                    if (item.action == null) return@forEach
                    this.inventoryConditions.add(InventoryCondition { player, slot, clickType, result ->
                        if (slot == item.index && (allowSpectatorClicks || player.gameMode != GameMode.SPECTATOR)) {
                            item.action.invoke(SlotClickEvent(player, this@Menu, item, clickType))
                            result.isCancel = item.cancelClicks

                            // If the click was cancelled, re-render the slot
                            if (item.cancelClicks) setItemStack(item.index,
                                item.itemStackBuilder(ItemStack.builder(item.material), player).build())
                        }
                    })
                }
                inventories[windowId] = this@Menu
            }.also { inventory ->
                if (!isPerPlayer) cachedInventory = inventory
                else cachedInventories[player] = inventory
            }
        }

        fun open(player: Player) {
            val inventory = getInventory(player)
            if (player.openInventory != inventory) {
                player.openInventory(inventory)
                onOpenedAction?.invoke(player)
            }
        }

        fun close(player: Player) {
            player.closeInventory()
        }

        fun setItemStack(player: Player, slot: Int, stack: ItemStack) {
            getInventory(player).setItemStack(slot, stack)
        }

        fun onOpened(function: (Player) -> Unit) {
            onOpenedAction = function
        }

        fun onTick(function: (Player) -> Unit) {
            onTickAction = function
        }

        fun onClosed(function: (Player) -> Unit) {
            onClosedAction = function
        }

        fun destroy(player: Player) {
            cachedInventories.remove(player)
        }

        fun rerender(player: Player) {
            if (isPerPlayer) {
                cachedInventories.remove(player)
                player.openInventory(getInventory(player))
            }
        }

        val viewers: Collection<Player>
            get() {
                require(!isPerPlayer) { "Per-player inventories are not supported" }
                return if (::cachedInventory.isInitialized) {
                    cachedInventory.viewers
                } else emptyList()
            }
    }

    class ItemsBuilder(private val inventoryType: InventoryType) {
        private val items = mutableListOf<Slot>()

        /**
         * Gets a slot's index based on row and column.
         * Both numbers start counting from 1.
         */
        fun pos(row: Int, column: Int) = (row - 1) * 9 + (column - 1)

        /**
         * Creates a border around the inventory by setting slots in the first and last columns and rows of the menu.
         */
        fun border(
            material: Material,
            itemStackBuilder: ItemStack.Builder.(player: Player) -> ItemStack.Builder,
            action: (SlotClickEvent.() -> Unit)? = null,
        ) {
            require(inventoryType.size % 9 == 0) { "InventoryType does not have a multiple of 9 slots." }
            require(inventoryType.size >= 9) { "InventoryType has less than 9 slots." }
            val rows = inventoryType.size / 9
            for (i in 1..rows) {
                // Top and bottom
                if (i == 1 || i == rows) {
                    for (j in 1 .. 9) {
                        slot(pos(i, j), material, itemStackBuilder, action)
                    }
                } else {
                    // Sides
                    slot(pos(i, 1), material, itemStackBuilder, action)
                    slot(pos(i, 9), material, itemStackBuilder, action)
                }
            }
        }

        /**
         * Creates a standard slot with an ItemStack that can not be picked up by the player.
         */
        fun slot(
            slotNumber: Int,
            material: Material,
            itemStackBuilder: ItemStack.Builder.(player: Player) -> ItemStack.Builder,
            action: (SlotClickEvent.() -> Unit)? = null,
        ) {
            items.add(Slot(slotNumber, material, itemStackBuilder, true, action))
        }

        /**
         * Creates a slot with an ItemStack that can be picked up by the player.
         * Useful for creating lootable inventories or custom storage menus.
         */
        fun clickableSlot(
            slotNumber: Int,
            material: Material,
            itemStackBuilder: ItemStack.Builder.(player: Player) -> ItemStack.Builder,
            action: (SlotClickEvent.() -> Unit)? = null,
        ) {
            items.add(Slot(slotNumber, material, itemStackBuilder, false, action))
        }

        // When the inventory is built, the items are made immutable.
        fun build() = items.toList()
    }

    data class SlotClickEvent(val player: Player, val menu: Menu, val slot: Slot, val clickType: ClickType)

    data class Slot(
        val index: Int,
        val material: Material,
        val itemStackBuilder: ItemStack.Builder.(player: Player) -> ItemStack.Builder,
        val cancelClicks: Boolean = true,
        val action: (SlotClickEvent.() -> Unit)?,
    )

}