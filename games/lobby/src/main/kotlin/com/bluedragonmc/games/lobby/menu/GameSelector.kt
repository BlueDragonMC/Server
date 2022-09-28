package com.bluedragonmc.games.lobby.menu

import com.bluedragonmc.games.lobby.GameEntry
import com.bluedragonmc.games.lobby.Lobby
import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_3
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.utils.miniMessage
import com.bluedragonmc.server.utils.noItalic
import com.bluedragonmc.server.utils.splitAndFormatLore
import com.bluedragonmc.server.utils.withGradient
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.inventory.InventoryType
import org.spongepowered.configurate.ConfigurationNode

class GameSelector(private val config: ConfigurationNode, private val parent: Lobby) : Lobby.LobbyMenu() {

    override lateinit var menu: GuiModule.Menu

    override fun populate() {
        val games = config.node("games").getList(GameEntry::class.java)!!
        menu = parent.getModule<GuiModule>().createMenu(Component.translatable("lobby.menu.game.title"), InventoryType.CHEST_1_ROW, true, true) {
            games.forEachIndexed { index, (game, category, desc, time, material) ->
                slot(index, material, { player ->
                    displayName(Component.text(game, Style.style(TextDecoration.BOLD)).noItalic()
                        .withGradient(BRAND_COLOR_PRIMARY_1, BRAND_COLOR_PRIMARY_3))
                    // Add the game's description from the config
                    val lore = splitAndFormatLore(miniMessage.deserialize(desc), ALT_COLOR_1, player).toMutableList()
                    // Add the category and ETA before the game description
                    lore.add(0, Component.text("$category \u2014 \u231a $time", NamedTextColor.RED).noItalic())
                    lore(lore)
                }) {
                    parent.getMenu<GameMenu>(game)?.open(player)
                }
            }
        }
    }
}