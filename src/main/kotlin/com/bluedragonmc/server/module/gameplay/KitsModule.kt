package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

/**
 * A highly customizable module that provides starting items to the player.
 * The module considers the default kit to be the first item in the `selectableKits` list.
 * The module can optionally show a kit selection menu. If this menu is not shown, the default kit is given to everyone.
 * When the game starts, the module automatically gives the player their selected kit, or the default kit if none was selected.
 * Use the `giveKit` function to manually give a player their selected kit.
 * When the module is unloaded, players keep their kits.
 */
class KitsModule(val showMenu: Boolean = false, val selectableKits: List<Kit>) : GameModule() {
    private val selectedKits = hashMapOf<Player, Kit>()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        // todo add support for unlockable kits
        // todo make this use a "player join game event" instead of spawn event
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (showMenu) {
                if (!parent.hasModule<GuiModule>()) {
                    logger.warn("Kits module used without GUI module. Creating GUI module with default settings.")
                    parent.use(GuiModule())
                }
                val menu = parent.getModule<GuiModule>().createMenu(title = Component.text("Select Kit"), inventoryType = InventoryType.CHEST_1_ROW, isPerPlayer = true) {
                    for (selectableKit in selectableKits) {
                        val index = selectableKits.indexOf(selectableKit)
                        slot(index, selectableKit.icon, { player ->
                            displayName(selectableKit.name)
                            lore(selectableKit.description)
                        }) {
                            selectedKits[this.player] = selectableKit
                            this.player.sendMessage(Component.text("You have selected the ", NamedTextColor.GREEN).append(selectableKit.name).append(Component.text(" kit.", NamedTextColor.GREEN)))
                            menu.close(this.player)
                        }
                    }
                }
                menu.open(event.player)
            }
        }
        eventNode.addListener(GameStartEvent::class.java) { event ->
            for (player in parent.players) giveKit(player)
        }
    }

    fun giveKit(player: Player, kit: Kit) {
        player.inventory.clear()
        for (item in kit.items) {
            player.inventory.setItemStack(item.key, item.value)
        }
    }

    /**
     * Gives the player their selected kit.
     * If they did not select a kit, gives them the default kit.
     */
    fun giveKit(player: Player) {
        giveKit(player, selectedKits.getOrDefault(player, selectableKits[0]))
    }

    data class Kit(val name: Component, val description: Component = Component.empty(), val icon: Material = Material.DIAMOND, val items: HashMap<Int, ItemStack>)
}