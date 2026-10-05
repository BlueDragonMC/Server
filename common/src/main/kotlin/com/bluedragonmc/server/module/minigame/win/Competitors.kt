package com.bluedragonmc.server.module.minigame.win

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player

/**
 * A competitor in the end-of-game standings. Wraps either a team or a solo player
 * so that both team and free-for-all games can use the same ranking logic.
 */
sealed class Competitor {
    abstract val displayName: Component
    abstract val players: Collection<Player>

    class TeamCompetitor(val team: TeamModule.Team) : Competitor() {
        override val displayName: Component
            get() = team.name +
                    Component.text(" (", NamedTextColor.GRAY) +
                    Component.join(
                        JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)),
                        team.players.map { Component.text(it.username, team.name.color()) }) +
                    Component.text(")", NamedTextColor.GRAY)
        override val players: Collection<Player> get() = team.players
        override fun equals(other: Any?) = other is TeamCompetitor && other.team.uuid == team.uuid
        override fun hashCode() = team.uuid.hashCode()
        override fun toString() = "TeamCompetitor(${team.name})"
    }

    class PlayerCompetitor(val player: Player) : Competitor() {
        override val displayName: Component
            get() = Component.text(
                player.username,
                (player as? CustomPlayer)?.permissionMetadata?.rankColor ?: player.displayName?.color()
            )
        override val players: Collection<Player> get() = listOf(player)
        override fun equals(other: Any?) = other is PlayerCompetitor && other.player.uuid == player.uuid
        override fun hashCode() = player.uuid.hashCode()
        override fun toString() = "PlayerCompetitor(${player.username})"
    }

    /** A one-off competitor used for arbitrary "name + players" declarations. */
    class CustomCompetitor(
        override val displayName: Component,
        override val players: Collection<Player>,
    ) : Competitor() {
        override fun toString() = "CustomCompetitor(${displayName})"
    }

    companion object {
        fun of(team: TeamModule.Team) = TeamCompetitor(team)
        fun of(player: Player) = PlayerCompetitor(player)
    }
}

/**
 * A rank and the competitors sharing it. Competitors in the same [Standing]
 * are tied with each other.
 */
data class Standing(
    val rank: Int,
    val competitors: List<Competitor>,
    val score: Double? = null,
)

/** How competitors are ordered to produce [WinModule.getStandings]. */
enum class Ranking {
    /** Derive the ranking from the win condition (survival for `LAST_*`, otherwise score). */
    AUTOMATIC,

    /** Later elimination is better. Competitors that are still alive rank first. */
    SURVIVAL,

    /** Later elimination is better, ignoring any notion of survivors (pure ordering). */
    ELIMINATION_ORDER,

    /** Higher reported score is better. */
    SCORE_DESC,

    /** Lower reported score is better. */
    SCORE_ASC,

    /** Do not track a ranking. */
    NONE,
}
