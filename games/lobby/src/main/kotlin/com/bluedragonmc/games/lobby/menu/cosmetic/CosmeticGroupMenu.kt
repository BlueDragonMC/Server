package com.bluedragonmc.games.lobby.menu.cosmetic

import com.bluedragonmc.games.lobby.Lobby
import com.bluedragonmc.server.*
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.database.CosmeticsModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.utils.noItalic
import com.bluedragonmc.server.utils.splitAndFormatLore
import com.bluedragonmc.server.utils.withColor
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Enchantment
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag

class CosmeticGroupMenu(private val parent: Lobby, private val groupId: String) : Lobby.LobbyMenu() {
    override lateinit var menu: GuiModule.Menu

    override fun populate() {
        val cosmetics = parent.getModule<CosmeticsModule>()
        val group = cosmetics.getGroup(groupId)!!
        val category = cosmetics.getCategories().find { it.groups.any { g -> g.id == groupId } }

        menu = parent.getModule<GuiModule>().createMenu(
            Component.translatable("lobby.menu.cosmetics.group", group.name),
            InventoryType.CHEST_6_ROW,
            true,
            true
        ) {
            group.cosmetics.forEachIndexed { i, cosmetic ->
                val c = cosmetics.withId(cosmetic.id)
                val itemStack = cosmetic.itemStack
                val material = itemStack.material()
                slot(i, material, { player ->
                    meta(itemStack.meta())
                    displayName(cosmetic.name.withColor(BRAND_COLOR_PRIMARY_2).noItalic())
                    val owned = cosmetics.hasCosmetic(player, c)
                    val equipped = owned && cosmetics.isCosmeticEquipped(player, c)
                    val balance = (player as CustomPlayer).data.coins

                    val status = if (equipped) {
                        Component.translatable("lobby.menu.cosmetics.equipped", BRAND_COLOR_PRIMARY_1)
                    } else if (owned) {
                        Component.translatable("lobby.menu.cosmetics.owned", BRAND_COLOR_PRIMARY_2)
                    } else if (balance >= cosmetic.cost) {
                        Component.translatable("lobby.menu.cosmetics.purchase", ALT_COLOR_2, Component.text(cosmetic.cost))
                    } else {
                        Component.translatable("lobby.menu.cosmetics.cannot_afford.short", NamedTextColor.RED, Component.text(balance), Component.text(cosmetic.cost))
                    }
                    val description = splitAndFormatLore(cosmetic.description, NamedTextColor.GRAY, player) + status.noItalic()
                    if (equipped) { // Add an enchantment glint to equipped cosmetics
                        meta { builder ->
                            builder.enchantment(Enchantment.PROTECTION, 1)
                            builder.setTag(Tag.Integer("HideFlags"), 1)
                        }
                    }

                    lore(description)
                }) {
                    val owned = cosmetics.hasCosmetic(player, c)
                    val equipped = owned && cosmetics.isCosmeticEquipped(player, c)

                    DatabaseModule.IO.launch {
                        if (owned && !equipped) {
                            cosmetics.equipCosmetic(player, c)
                            player.sendMessage(Component.translatable("lobby.menu.cosmetics.equip.success", BRAND_COLOR_PRIMARY_2, cosmetic.name))
                            menu.rerender(player)
                        } else if (!owned) {
                            if (cosmetics.canAffordCosmetic(player, c)) {
                                cosmetics.purchaseCosmetic(player, c)
                                player.sendMessage(Component.translatable("lobby.menu.cosmetics.purchase.success", NamedTextColor.GREEN, cosmetic.name))
                                menu.rerender(player)
                            } else {
                                player.sendMessage(Component.translatable("lobby.menu.cosmetics.cannot_afford", NamedTextColor.RED, cosmetic.name))
                            }
                        } else {
                            player.sendMessage(Component.translatable("lobby.menu.cosmetics.already_equipped", NamedTextColor.RED, cosmetic.name))
                        }
                    }
                }
            }

            // Unequip button
            slot(53, Material.REDSTONE_BLOCK, {
                displayName(Component.translatable("lobby.menu.cosmetics.unequip", NamedTextColor.RED).noItalic())
            }) {
                DatabaseModule.IO.launch {
                    cosmetics.unequipCosmeticsInGroup(player, groupId)
                    player.sendMessage(Component.translatable("lobby.menu.cosmetics.unequip.success", NamedTextColor.RED, Component.translatable(groupId)))
                    menu.rerender(player)
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
                if (category != null) {
                    parent.getMenu<CosmeticCategoryMenu>(category.id)?.open(player)
                } else {
                    parent.getMenu<CosmeticsMenu>()?.open(player)
                }
            }
        }
    }
}
