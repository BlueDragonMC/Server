package com.bluedragonmc.server.module

import com.bluedragonmc.server.Game
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
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
class GuiModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {}

    fun createMenu(
        title: Component,
        inventoryType: InventoryType,
        items: ItemsBuilder.() -> Unit,
        isPerPlayer: Boolean = true
    ): Menu {
        val builder = ItemsBuilder(inventoryType)
        items(builder)
        return Menu(title, inventoryType, builder.build(), isPerPlayer)
    }

    data class Menu(
        val title: Component,
        val inventoryType: InventoryType,
        private val items: List<Slot>,
        private val isPerPlayer: Boolean
    ) {

        private lateinit var cachedInventory: Inventory

        private fun getInventory(player: Player): Inventory {
            if(!isPerPlayer && this::cachedInventory.isInitialized) return cachedInventory
            return Inventory(inventoryType, title).apply {
                items.forEach { item ->
                    setItemStack(item.index, item.itemStackBuilder(ItemStack.builder(item.material), player).build())
                    if (item.action != null) {
                        this.inventoryConditions.add(InventoryCondition { player, slot, clickType, inventoryConditionResult ->
                            if (slot == item.index) {
                                item.action.invoke(SlotClickEvent(player, this@Menu, item, clickType))
                                inventoryConditionResult.isCancel = item.cancelClicks
                            }
                        })
                    }
                }
            }.also { inventory ->
                if(!isPerPlayer) cachedInventory = inventory
            }
        }

        fun open(player: Player) {
            val inventory = getInventory(player)
            if (player.openInventory != inventory) {
                player.openInventory(inventory)
            }
        }

        fun close(player: Player) {
            player.closeInventory()
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
            action: (SlotClickEvent.() -> Unit)? = null
        ) {
            require(inventoryType.size % 9 == 0) { "InventoryType does not have a multiple of 9 slots." }
            require(inventoryType.size >= 9) { "InventoryType has less than 9 slots." }
            val rows = inventoryType.size / 9
            for (i in 1..rows) {
                slot(pos(i, 1), material, itemStackBuilder, action)
                slot(pos(i, 9), material, itemStackBuilder, action)
            }
        }

        /**
         * Creates a standard slot with an ItemStack that can not be picked up by the player.
         */
        fun slot(
            slotNumber: Int,
            material: Material,
            itemStackBuilder: ItemStack.Builder.(player: Player) -> ItemStack.Builder,
            action: (SlotClickEvent.() -> Unit)? = null
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
            action: (SlotClickEvent.() -> Unit)? = null
        ) {
            items.add(Slot(slotNumber, material, itemStackBuilder, false, action))
        }

        // When the inventory is built, the items are made non-mutable.
        fun build() = items.toList()
    }

    data class SlotClickEvent(val player: Player, val menu: Menu, val slot: Slot, val clickType: ClickType)

    data class Slot(
        val index: Int,
        val material: Material,
        val itemStackBuilder: ItemStack.Builder.(player: Player) -> ItemStack.Builder,
        val cancelClicks: Boolean = true,
        val action: (SlotClickEvent.() -> Unit)?
    )

}