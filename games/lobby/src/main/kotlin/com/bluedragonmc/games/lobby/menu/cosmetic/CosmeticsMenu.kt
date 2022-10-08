package com.bluedragonmc.games.lobby.menu.cosmetic

import com.bluedragonmc.games.lobby.Lobby
import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.ALT_COLOR_2
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.database.CosmeticsModule
import com.bluedragonmc.server.utils.noItalic
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material

class CosmeticsMenu(private val parent: Lobby) : Lobby.LobbyMenu() {
    override lateinit var menu: GuiModule.Menu

    override fun populate() {
        val cosmetics = parent.getModule<CosmeticsModule>()
        menu = parent.getModule<GuiModule>().createMenu(Component.translatable("lobby.menu.cosmetics"), InventoryType.CHEST_6_ROW, true, true) {
            cosmetics.getCategories().forEachIndexed { i, category ->
                slot(i, category.material, {
                    displayName(category.name.colorIfAbsent(BRAND_COLOR_PRIMARY_1).noItalic())
                }) {
                    parent.getMenu<CosmeticCategoryMenu>(category.id)?.open(player)
                }
            }

            // Coin indicator
            slot(49, Material.SUNFLOWER, { player ->
                val coins = (player as CustomPlayer).data.coins
                displayName(
                    Component.translatable(
                        "lobby.menu.cosmetics.total_coins", ALT_COLOR_1,
                        Component.text(coins, ALT_COLOR_2, TextDecoration.BOLD)
                    ).noItalic()
                )
            })
        }
    }
}