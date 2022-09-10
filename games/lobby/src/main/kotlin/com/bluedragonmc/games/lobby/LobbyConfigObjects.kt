package com.bluedragonmc.games.lobby

import com.mongodb.internal.operation.OrderBy
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.entity.metadata.other.ItemFrameMeta
import net.minestom.server.item.Material
import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class ConfigurableNPC(
    val pos: Pos = Pos.ZERO,
    val name: Component = Component.empty(),
    val entityType: EntityType? = null,
    val skin: PlayerSkin? = null,
    val game: String? = null,
    val menu: String? = null,
    val map: String? = null,
    val mode: String? = null,
    val lookAt: Pos? = null
)

@ConfigSerializable
data class GameEntry(
    val game: String = "???",
    val category: String = "???",
    val description: String = "???",
    val time: String = "\u221e",
    val material: Material = Material.RED_STAINED_GLASS
)

@ConfigSerializable
data class Leaderboard(
    val statistic: String = "",
    val title: String = "Failed to load leaderboard",
    val subtitle: String = "",
    val show: Int = 10,
    val displayMode: DisplayMode = DisplayMode.WHOLE_NUMBER,
    val orderBy: OrderBy = OrderBy.DESC,
    val topLeft: Pos = Pos.ZERO,
    val bottomRight: Pos = Pos.ZERO,
    val orientation: ItemFrameMeta.Orientation = ItemFrameMeta.Orientation.EAST
) {
    enum class DisplayMode {
        DURATION, DECIMAL, WHOLE_NUMBER
    }
}

@ConfigSerializable
data class LeaderboardCategory(
    val name: String = "",
    val description: String = "",
    val icon: Material = Material.WHITE_STAINED_GLASS,
    val leaderboards: List<LeaderboardEntry> = emptyList()
)

@ConfigSerializable
data class LeaderboardEntry(
    val title: String = "",
    val subtitle: String = "",
    val icon: Material = Material.WHITE_STAINED_GLASS,
    val statistic: String = "",
    val displayMode: Leaderboard.DisplayMode = Leaderboard.DisplayMode.WHOLE_NUMBER,
    val orderBy: OrderBy = OrderBy.DESC
)