package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.GuiModule.ItemsBuilder
import com.bluedragonmc.server.module.GuiModule.Menu
import com.bluedragonmc.server.utils.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.trait.CancellableEvent
import net.minestom.server.event.trait.PlayerInstanceEvent
import net.minestom.server.inventory.InventoryType
import net.minestom.server.inventory.PlayerInventory
import net.minestom.server.inventory.TransactionOption
import net.minestom.server.inventory.TransactionType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

@DependsOn(GuiModule::class)
class ShopModule : GameModule() {

    private lateinit var parent: Game

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
    }

    fun createShop(title: Component, shopItemsBuilder: ShopItemsBuilder.() -> Unit): Shop {
        val menu = parent.getModule<GuiModule>().createMenu(title, InventoryType.CHEST_6_ROW, true) {
            shopItemsBuilder(ShopItemsBuilder(this@ShopModule, this@createMenu))
        }
        menu.onOpened { player -> menu.rerender(player) }
        return Shop(menu)
    }

    class Shop(private val menu: Menu) {
        fun open(player: Player) = menu.open(player)
        fun close(player: Player) = menu.close(player)
    }

    class ShopItemsBuilder(private val module: ShopModule, private val itemsBuilder: ItemsBuilder) {

        fun teamUpgrade(
            row: Int,
            column: Int,
            price: Int,
            currency: Material,
            virtualItem: VirtualItem,
            displayOverride: (Player) -> ItemStack,
        ) =
            item(row, column, displayOverride, price, currency, virtualItem)

        fun teamUpgrade(row: Int, column: Int, price: Int, currency: Material, virtualItem: VirtualItem) =
            item(row, column, { ItemStack.of(virtualItem.displayItem) }, price, currency, virtualItem)

        fun item(row: Int, column: Int, itemStack: ItemStack, price: Int, currency: Material) =
            item(row, column, { itemStack }, price, currency)

        fun item(row: Int, column: Int, material: Material, price: Int, currency: Material) =
            item(row, column, material, 1, price, currency)

        fun item(row: Int, column: Int, material: (Player) -> Material, amount: Int, price: Int, currency: Material) =
            item(row, column, { player -> ItemStack.of(material(player), amount) }, price, currency)

        fun item(row: Int, column: Int, material: Material, amount: Int, price: Int, currency: Material) =
            item(row, column, { ItemStack.of(material, amount) }, price, currency)

        fun item(
            row: Int,
            column: Int,
            itemStackProvider: (Player) -> ItemStack,
            price: Int,
            currency: Material,
            virtualItem: VirtualItem? = null,
        ) {
            itemsBuilder.slot(
                itemsBuilder.pos(row, column),
                { player -> itemStackProvider(player).material() },
                { player ->
                    val itemStack = itemStackProvider(player)
                    amount(itemStack.amount())
                    if (virtualItem != null) {
                        set(DataComponents.ITEM_NAME, virtualItem.name)
                    } else {
                        set(
                            DataComponents.ITEM_NAME,
                            itemStack.material().displayName(NamedTextColor.WHITE) + Component.text(
                                " x${itemStack.amount()}",
                                NamedTextColor.GRAY
                            )
                        )
                    }

                    val info = listOf(
                        // Price
                        Component.text("Price: ", NamedTextColor.GRAY).noItalic() + Component.text(
                            "$price ", NamedTextColor.WHITE
                        ).noItalic() + currency.displayName(NamedTextColor.WHITE).noItalic(),
                        // Whether the player can afford the item or not
                        if (virtualItem?.isOwnedBy(player) == true)
                            Component.translatable("module.shop.already_owned", NamedTextColor.RED).noItalic()
                        else if (player.inventory.takeItemStack(
                                ItemStack.of(currency, price), TransactionOption.DRY_RUN
                            )
                        ) Component.translatable("module.shop.click_to_purchase", NamedTextColor.GREEN).noItalic()
                        else Component.translatable(
                            "module.shop.not_enough_currency",
                            NamedTextColor.RED,
                            currency.displayName()
                        ).noItalic()
                    )

                    if (virtualItem != null) {
                        // Display team upgrade descriptions if applicable
                        set(
                            DataComponents.LORE,
                            splitAndFormatLore(virtualItem.description, ALT_COLOR_1, player) + info
                        )

                        if (virtualItem.eventNode.parent == null) {
                            module.eventNode.addChild(virtualItem.eventNode)
                        }
                    } else set(DataComponents.LORE, info)

                    set(DataComponents.ENCHANTMENTS, itemStack.get(DataComponents.ENCHANTMENTS))
                }) {
                if (virtualItem != null) {
                    buyVirtualItem(this.player, virtualItem, price, currency)
                } else {
                    buyItem(this.player, itemStackProvider(this.player), price, currency)
                }
            }
        }

        private fun buyItem(player: Player, item: ItemStack, price: Int, currency: Material) {
            val removeSuccess =
                player.inventory.takeItemStack(ItemStack.of(currency, price), TransactionOption.ALL_OR_NOTHING)
            val addSuccess = player.inventory.addItemStack(item, TransactionOption.DRY_RUN)
            if (!removeSuccess) {
                player.sendMessage(
                    Component.translatable(
                        "module.shop.not_enough_currency",
                        NamedTextColor.RED,
                        currency.displayName()
                    )
                )
                return
            }
            val event = ShopPurchaseEvent.Item(module.parent, player, price, currency, item)
            EventDispatcher.call(event)
            if (event.isCancelled) return
            if (!addSuccess) {
                player.sendMessage(Component.translatable("module.shop.no_inventory_space", NamedTextColor.RED))
                // Refund the player's currency
                player.inventory.addItemStack(ItemStack.of(currency, price), TransactionOption.ALL)
                return
            }
            if (item.has(DataComponents.EQUIPPABLE)) {
                // Prefer the item's equipment slot if possible
                val slot = item.get(DataComponents.EQUIPPABLE)!!.slot
                val current = player.inventory.getEquipment(slot, player.heldSlot)
                if (current.isAir || (item.isSimilar(current) && item.amount() + current.amount() <= item.maxStackSize())) {
                    player.inventory.setEquipment(
                        slot,
                        player.heldSlot,
                        if (current.isAir) item else current.withAmount { it + item.amount() })
                    return
                }
            }
            player.inventory.addItemStack(item, TransactionOption.ALL)
        }

        private fun buyVirtualItem(player: Player, item: VirtualItem, price: Int, currency: Material) {
            val event = ShopPurchaseEvent.VirtualItem(module.parent, player, price, currency, item)
            EventDispatcher.call(event)
            if (event.isCancelled) return
            if (item.isOwnedBy(player)) {
                player.sendMessage(Component.translatable("module.shop.already_owned", NamedTextColor.RED))
                return
            }

            val removeSuccess =
                player.inventory.takeItemStack(ItemStack.of(currency, price), TransactionOption.ALL_OR_NOTHING, true)

            if (!removeSuccess) {
                player.sendMessage(
                    Component.translatable(
                        "module.shop.not_enough_currency",
                        NamedTextColor.RED,
                        currency.displayName()
                    )
                )
                return
            }
            (player as CustomPlayer).virtualItems.add(item)
            item.obtainedCallback(player, item)
        }
    }

    sealed class ShopPurchaseEvent(
        game: Game,
        private val player: Player,
        val price: Int,
        val currency: Material
    ) : PlayerInstanceEvent, CancellableEvent, GameEvent(game) {
        private var isCancelled = false

        override fun getPlayer() = this.player
        override fun isCancelled() = this.isCancelled
        override fun setCancelled(cancel: Boolean) {
            this.isCancelled = cancel
        }

        class Item(game: Game, player: Player, price: Int, currency: Material, val itemStack: ItemStack) :
            ShopPurchaseEvent(game, player, price, currency)

        class VirtualItem(
            game: Game,
            player: Player,
            price: Int,
            currency: Material,
            val virtualItem: ShopModule.VirtualItem
        ) :
            ShopPurchaseEvent(game, player, price, currency)
    }

    /**
     * Represents a non-tangible item that players can own which has
     * some metadata, like a name and description. Each VirtualItem
     * has its own filtered event node, which means VirtualItem
     * instances SHOULD NOT BE CACHED between Game instances.
     * This would cause unexpected behavior. It is intended to use the same
     * VirtualItem for multiple players across the same game, though.
     */
    open class VirtualItem(
        val name: Component,
        val description: Component = Component.empty(),
        val displayItem: Material = Material.AIR,
        val obtainedCallback: (Player, VirtualItem) -> Unit,
    ) {
        open fun isOwnedBy(player: Player) = (player as CustomPlayer).virtualItems.contains(this)

        /**
         * An event node that filters for players which own the item.
         * If this VirtualItem is part of a shop managed by the [ShopModule],
         * this node will automatically be registered when the item is added
         * to any shop.
         */
        val eventNode =
            EventNode.event("virtualitem-${name.toPlainText()}-owners", EventFilter.PLAYER) { event ->
                isOwnedBy(event.player)
            }
    }
}

fun <T> PlayerInventory.takeItemStack(
    itemStack: ItemStack,
    tx: TransactionOption<T>,
    takeFromEquipmentSlots: Boolean
): T {
    val maxSlot = if (takeFromEquipmentSlots) this.size else this.innerSize
    val result = TransactionType.TAKE.process(this, itemStack, { _, _ -> true }, 0, maxSlot, 1)
    return tx.fill(this, result.left(), result.right())
}