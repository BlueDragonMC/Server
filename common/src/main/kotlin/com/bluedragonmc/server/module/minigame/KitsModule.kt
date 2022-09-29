package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.KitSelectedEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.utils.splitAndFormatLore
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
@DependsOn(GuiModule::class)
class KitsModule(
    val showMenu: Boolean = false,
    val giveKitsOnStart: Boolean = true,
    val giveKitsOnSelect: Boolean = false,
    val selectableKits: List<Kit>,
) : GameModule() {

    private lateinit var parent: Game

    private val selectedKits = hashMapOf<Player, Kit>()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        // todo add support for unlockable kits
        // todo make this use a "player join game event" instead of spawn event
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (showMenu) {
                selectKit(event.player)
            }
        }
        eventNode.addListener(GameStartEvent::class.java) { event ->
            if (giveKitsOnStart) for (player in parent.players) giveKit(player)
        }
    }

    /**
     * Displays the kit selection menu to the specified player.
     */
    fun selectKit(player: Player) {
        val menu = parent.getModule<GuiModule>().createMenu(title = Component.translatable("module.kit.menu.title"), inventoryType = InventoryType.CHEST_1_ROW, isPerPlayer = true) {
            for (selectableKit in selectableKits) {
                val index = selectableKits.indexOf(selectableKit)
                slot(index, selectableKit.icon, { player ->
                    displayName(selectableKit.name)
                    lore(splitAndFormatLore(selectableKit.description, NamedTextColor.GRAY, player))
                }) {
                    selectedKits[this.player] = selectableKit
                    this.player.sendMessage(Component.translatable("module.kit.selected", NamedTextColor.GREEN, selectableKit.name))
                    if (giveKitsOnSelect) giveKit(this.player, selectableKit)
                    menu.close(this.player)
                    parent.callEvent(KitSelectedEvent(parent, this.player, selectableKit))
                }
            }
        }
        menu.open(player)
    }

    /**
     * Gives the player all the items in a specific kit.
     */
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

    fun getSelectedKit(player: Player): Kit = selectedKits.getOrDefault(player, selectableKits[0])

    fun hasAbility(player: Player, ability: String): Boolean = getSelectedKit(player).abilities.contains(ability)

    data class Kit(
        val name: Component,
        val description: Component = Component.empty(),
        val icon: Material = Material.DIAMOND,
        val items: HashMap<Int, ItemStack> = hashMapOf(),
        val abilities: List<String> = emptyList()
    ) {
        fun hasAbility(ability: String) = abilities.contains(ability)
    }
}