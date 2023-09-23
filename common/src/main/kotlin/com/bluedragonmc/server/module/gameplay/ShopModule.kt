package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.utils.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.inventory.InventoryType
import net.minestom.server.inventory.TransactionOption
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

class ShopModule : GuiModule() {

    private lateinit var parent: Game

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
    }

    fun createShop(title: Component, shopItemsBuilder: ShopItemsBuilder.() -> Unit): Shop {
        val menu = createMenu(title, InventoryType.CHEST_6_ROW, true) {
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
                    if (virtualItem != null) {
                        displayName(virtualItem.name.noItalic())
                    } else {
                        displayName(
                            itemStack.material().displayName(NamedTextColor.WHITE)
                                .noItalic() + Component.text(" x${itemStack.amount()}", NamedTextColor.GRAY).noItalic()
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
                        lore(splitAndFormatLore(virtualItem.description, ALT_COLOR_1, player) + info)

                        if (virtualItem.eventNode.parent == null) {
                            module.eventNode.addChild(virtualItem.eventNode)
                        }
                    } else lore(info)

                    meta { metaBuilder ->
                        metaBuilder.enchantments(itemStack.meta().enchantmentMap)
                    }
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
            if (!addSuccess) {
                player.sendMessage(Component.translatable("module.shop.no_inventory_space", NamedTextColor.RED))
                return
            }
            player.inventory.addItemStack(item, TransactionOption.ALL)
        }

        private fun buyVirtualItem(player: Player, item: VirtualItem, price: Int, currency: Material) {
            if (item.isOwnedBy(player)) {
                player.sendMessage(Component.translatable("module.shop.already_owned", NamedTextColor.RED))
                return
            }
            val removeSuccess =
                player.inventory.takeItemStack(ItemStack.of(currency, price), TransactionOption.ALL_OR_NOTHING)
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