package com.bluedragonmc.games.lobby.menu

import com.bluedragonmc.api.grpc.CommonTypes.GameType.GameTypeFieldSelector
import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.games.lobby.GameEntry
import com.bluedragonmc.games.lobby.Lobby
import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.database.Permissions
import com.bluedragonmc.server.utils.miniMessage
import com.bluedragonmc.server.utils.noItalic
import com.bluedragonmc.server.utils.splitAndFormatLore
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material

class GameMenu(private val config: GameEntry, private val parent: Lobby) : Lobby.LobbyMenu() {

    override lateinit var menu: GuiModule.Menu

    override fun populate() {
        val title = Component.text(config.game)
        menu = parent.getModule<GuiModule>().createMenu(title, InventoryType.CHEST_5_ROW, true, true) {

            // Border
            border(Material.BLUE_STAINED_GLASS_PANE, { displayName(Component.empty()) })

            // Quick join
            slot(pos(3, 4), Material.MAP, {
                displayName(Component.translatable("lobby.menu.game.quick_join", BRAND_COLOR_PRIMARY_2).noItalic())
            }) {
                Environment.current.queue.queue(player, gameType {
                    name = config.game
                    selectors += GameTypeFieldSelector.GAME_NAME
                })
                menu.close(player)
            }

            // Game name and information
            slot(pos(3, 5), config.material, { player ->
                displayName(title.noItalic() withColor ALT_COLOR_1)
                lore(splitAndFormatLore(miniMessage.deserialize(config.description), NamedTextColor.GRAY, player))
            })

            // Map select
            slot(pos(3, 6), Material.FILLED_MAP, { player ->
                val hasPermission = Permissions.hasPermission((player as CustomPlayer).data, "command.game.map")
                if (hasPermission) {
                    displayName(Component.translatable("lobby.menu.game.map_select", BRAND_COLOR_PRIMARY_2).noItalic())
                } else {
                    displayName(Component.translatable("lobby.menu.game.map_select.no_permission", NamedTextColor.RED).noItalic())
                }
            }) {
                val hasPermission = Permissions.hasPermission((player as CustomPlayer).data, "command.game.map")
                if (hasPermission) {
                    parent.getMenu<MapSelectMenu>(config.game)?.open(player)
                } else {
                    player.sendMessage(Component.translatable("lobby.menu.game.map_select.no_permission", NamedTextColor.RED))
                    menu.close(player)
                }
            }

            // Back button (bottom left)
            slot(pos(5, 1), Material.ARROW, {
                displayName(Component.translatable("lobby.menu.back", NamedTextColor.RED).noItalic())
            }) {
                parent.getMenu<GameSelector>()?.open(player)
            }

        }
    }
}