package com.bluedragonmc.games.lobby

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.utils.formatDuration
import com.bluedragonmc.server.utils.noItalic
import com.bluedragonmc.server.utils.plus
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.spongepowered.configurate.ConfigurationNode

class LeaderboardBrowser(private val config: ConfigurationNode, private val parent: Game) : Lobby.LobbyMenu() {

    override lateinit var menu: GuiModule.Menu

    override fun populate() {
        val categories = config.node("leaderboard-browser").getList(LeaderboardCategory::class.java)!!
        menu = parent.getModule<GuiModule>()
            .createMenu(Component.translatable("lobby.menu.lb.title"), InventoryType.CHEST_3_ROW, false, true) {
                categories.forEachIndexed { categoryIndex, category ->
                    // Build a menu for each leaderboard in the category
                    val lbMenu = createCategoryMenu(category)
                    slot(categoryIndex, category.icon, {
                        displayName(Component.translatable(category.name, BRAND_COLOR_PRIMARY_1).noItalic())
                    }) { lbMenu.open(player) }
                }
                slot(26, Material.ARROW, {
                    displayName(Component.translatable("lobby.menu.lb.exit", NamedTextColor.RED).noItalic())
                }) { menu.close(player) }
            }
    }

    private fun createCategoryMenu(category: LeaderboardCategory) = parent.getModule<GuiModule>()
        .createMenu(Component.translatable(category.name), InventoryType.CHEST_3_ROW, false, true) {
            slot(26, Material.ARROW, {
                displayName(Component.translatable("lobby.menu.lb.back",
                    NamedTextColor.RED,
                    Component.translatable(category.name)).noItalic())
            }) { open(player) }
            for ((entryIndex, entry) in category.leaderboards.withIndex()) {
                slot(entryIndex, entry.icon, {
                    buildLeaderboardItem(this, entry)
                })
            }
        }

    private fun buildLeaderboardItem(builder: ItemStack.Builder, entry: LeaderboardEntry) = builder.apply {
        displayName(Component.translatable(entry.title, BRAND_COLOR_PRIMARY_2).noItalic())

        val leaderboardComponent = runBlocking {
            parent.getModule<StatisticsModule>().rankPlayersByStatistic(entry.statistic, entry.orderBy)
        }.map { (doc, value) ->
            Component.text(doc.username, doc.highestGroup?.color ?: NamedTextColor.GRAY)
                .noItalic() + Component.text(": ", BRAND_COLOR_PRIMARY_2) + Component.text(formatValue(value,
                entry.displayMode), BRAND_COLOR_PRIMARY_1)
        }

        if (entry.subtitle.isNotEmpty()) {
            lore(Component.translatable(entry.subtitle, BRAND_COLOR_PRIMARY_1).noItalic(),
                Component.empty(),
                *leaderboardComponent.toTypedArray())
        } else {
            lore(*leaderboardComponent.toTypedArray())
        }
    }

    companion object {
        internal fun formatValue(value: Double, displayMode: Leaderboard.DisplayMode) = when (displayMode) {
            Leaderboard.DisplayMode.DURATION -> formatDuration(value.toLong())
            Leaderboard.DisplayMode.DECIMAL -> String.format("%.2f", value)
            Leaderboard.DisplayMode.WHOLE_NUMBER -> value.toInt().toString()
        }
    }
}
