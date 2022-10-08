package com.bluedragonmc.games.lobby.menu.cosmetic

import com.bluedragonmc.games.lobby.Lobby
import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.ALT_COLOR_2
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.database.CosmeticsModule
import com.bluedragonmc.server.utils.noItalic
import com.bluedragonmc.server.utils.splitAndFormatLore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material

class CosmeticCategoryMenu(private val parent: Lobby, private val categoryId: String) : Lobby.LobbyMenu() {
    override lateinit var menu: GuiModule.Menu

    override fun populate() {
        val cosmetics = parent.getModule<CosmeticsModule>()
        val category = cosmetics.getCategory(categoryId)!!
        menu = parent.getModule<GuiModule>().createMenu(
            Component.translatable("lobby.menu.cosmetics.category", category.name),
            InventoryType.CHEST_6_ROW,
            true,
            true
        ) {
            category.groups.forEachIndexed { i, group ->
                slot(i, group.material, { player ->
                    displayName(group.name.colorIfAbsent(BRAND_COLOR_PRIMARY_2).noItalic())
                    lore(splitAndFormatLore(group.description, NamedTextColor.GRAY, player))
                }) {
                    parent.getMenu<CosmeticGroupMenu>(group.id)?.open(player)
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

            // Back button
            slot(45, Material.ARROW, {
                displayName(Component.translatable("lobby.menu.back", NamedTextColor.RED).noItalic())
            }) {
                parent.getMenu<CosmeticsMenu>()?.open(player)
            }
        }
    }
}