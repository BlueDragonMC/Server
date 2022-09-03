package com.bluedragonmc.server.game.lobby

import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.ALT_COLOR_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.game.Lobby
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.utils.noItalic
import com.bluedragonmc.server.utils.splitAndFormatLore
import net.kyori.adventure.text.Component
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material
import org.spongepowered.configurate.ConfigurationNode

class LobbyShop(config: ConfigurationNode, private val parent: Game) : Lobby.LobbyMenu() {

    override lateinit var menu: GuiModule.Menu

    override fun populate() {
        menu = parent.getModule<GuiModule>().createMenu(
            Component.translatable("lobby.shop.coming_soon"),
                InventoryType.CHEST_5_ROW,
                isPerPlayer = true,
                allowSpectatorClicks = true
        ) {
            slot(22, Material.BELL, { player ->
                displayName(Component.translatable("lobby.shop.coming_soon", ALT_COLOR_1).noItalic())
                lore(splitAndFormatLore(Component.translatable("lobby.shop.coming_soon.lore"), ALT_COLOR_2, player))
            })
        }
    }
}
