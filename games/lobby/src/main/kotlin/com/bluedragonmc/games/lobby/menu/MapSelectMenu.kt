package com.bluedragonmc.games.lobby.menu

import com.bluedragonmc.api.grpc.CommonTypes.GameType.GameTypeFieldSelector
import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.games.lobby.Lobby
import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.utils.noItalic
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material

class MapSelectMenu(private val gameName: String, private val parent: Lobby) : Lobby.LobbyMenu() {

    override lateinit var menu: GuiModule.Menu

    override fun populate() {
        val maps = Environment.queue.getMaps(gameName) ?: emptyArray()
        menu = parent.getModule<GuiModule>().createMenu(Component.translatable("lobby.menu.game.map_select"), InventoryType.CHEST_3_ROW, false, true) {
            maps.forEachIndexed { index, file ->
                val mapName = file.name
                slot(index, Material.FILLED_MAP, {
                    displayName(Component.text(mapName, ALT_COLOR_1).noItalic())
                }) {
                    Environment.queue.queue(player, gameType {
                        name = gameName
                        this.mapName = mapName
                        selectors += GameTypeFieldSelector.GAME_NAME
                        selectors += GameTypeFieldSelector.MAP_NAME
                    })
                    menu.close(player)
                }
            }

            // Back button
            slot(26, Material.ARROW, {
                displayName(Component.translatable("lobby.menu.back", NamedTextColor.RED).noItalic())
            }) {
                parent.getMenu<GameMenu>(gameName)?.open(player)
            }
        }
    }
}