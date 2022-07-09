package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.toPlainText
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.MinecraftServer
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

/**
 * A module that provides team support.
 * This module can automatically generate teams when the game starts, or teams can be created manually and added to the `teams` list.
 */
class TeamModule(
    private val autoTeams: Boolean = false,
    private val autoTeamMode: AutoTeamMode = AutoTeamMode.PLAYER_COUNT,
    private val autoTeamCount: Int = 2,
    private val allowFriendlyFire: Boolean = false
) : GameModule() {
    val teams = mutableListOf<Team>()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(GameStartEvent::class.java) {
            // Auto team system
            if (autoTeams) {
                logger.info("Splitting ${parent.players.size} players into teams using strategy $autoTeamMode")
                when (autoTeamMode) {
                    AutoTeamMode.PLAYER_COUNT -> {
                        var teamNumber = 0
                        teams.addAll(parent.players.chunked(autoTeamCount) { players ->
                            Team(teamNumToName(teamNumber++), players.toMutableList(), allowFriendlyFire)
                        })
                        logger.info("Created ${teams.size} teams with $autoTeamCount players per team.")
                    }
                    AutoTeamMode.TEAM_COUNT -> {
                        val teamCount = autoTeamCount
                        val playersPerTeam = (parent.players.size / teamCount).coerceAtLeast(1)
                        var teamNumber = 0
                        teams.addAll(parent.players.chunked(playersPerTeam) { players ->
                            Team(teamNumToName(teamNumber++), players.toMutableList(), allowFriendlyFire)
                        })
                        logger.info("Created ${teams.size} teams with $playersPerTeam players per team.")
                    }
                }
            } else logger.info("Automatic team creation is disabled.")

            logger.info(teams.toString())

            teams.forEach { team ->
                team.sendMessage(Component.text("You are on ", NamedTextColor.GREEN).append(team.name))
                val scoreboardTeam = MinecraftServer.getTeamManager().createTeam(
                    parent.getInstance().uniqueId.toString() + "-" + team.name.toPlainText(),
                    team.name,
                    Component.text( // Prefix
                        team.name.toPlainText().first() + " ", // The first letter of the team's name
                        team.name.color() ?: NamedTextColor.WHITE, // The team name's text color
                        TextDecoration.BOLD
                    ),
                    NamedTextColor.nearestTo(
                        team.name.color() ?: NamedTextColor.WHITE
                    ), // Used for coloring player usernames
                    Component.empty() // No suffix
                )
                scoreboardTeam.isAllowFriendlyFire = allowFriendlyFire
                scoreboardTeam.sendUpdatePacket()
                team.players.forEach { player ->
                    scoreboardTeam.addMember(player.username)
                }
            }
        }
        eventNode.addListener(OldCombatModule.PlayerAttackEvent::class.java) { event ->
            if (event.target is Player) {
                // Check for friendly fire
                // This listen will only fire if a misbehaving client attacks a player on its team
                val attackerTeam = teams.find { it.players.contains(event.entity) } ?: return@addListener
                if (attackerTeam.allowFriendlyFire) return@addListener
                val targetTeam = teams.find { it.players.contains(event.target) } ?: return@addListener
                if (attackerTeam == targetTeam) event.isCancelled = true
            }
        }
    }

    /**
     * Converts a team number into a color.
     * An easy way to name a team.
     */
    fun teamNumToName(num: Int): Component {
        return when (num) {
            0 -> {
                Component.text("Red", NamedTextColor.RED)
            }
            1 -> {
                Component.text("Blue", NamedTextColor.BLUE)
            }
            2 -> {
                Component.text("Green", NamedTextColor.GREEN)
            }
            3 -> {
                Component.text("Aqua", NamedTextColor.AQUA)
            }
            4 -> {
                Component.text("Pink", NamedTextColor.LIGHT_PURPLE)
            }
            5 -> {
                Component.text("White", NamedTextColor.WHITE)
            }
            6 -> {
                Component.text("Gray", NamedTextColor.GRAY)
            }
            7 -> {
                Component.text("Yellow", NamedTextColor.YELLOW)
            }
            8 -> {
                Component.text("Gold", NamedTextColor.GOLD)
            }
            9 -> {
                Component.text("Purple", NamedTextColor.DARK_PURPLE)
            }
            10 -> {
                Component.text("Dark Green", NamedTextColor.DARK_GREEN)
            }
            11 -> {
                Component.text("Dark Aqua", NamedTextColor.DARK_AQUA)
            }
            12 -> {
                Component.text("Dark Red", NamedTextColor.DARK_RED)
            }
            13 -> {
                Component.text("Dark Gray", NamedTextColor.DARK_GRAY)
            }
            14 -> {
                Component.text("Dark Blue", NamedTextColor.DARK_BLUE)
            }
            else -> {
                Component.text("Team $num", NamedTextColor.AQUA)
            }
        }
    }

    /**
     * Returns the team that the specified player is currently on, or null if the player is not on a team.
     * Note: if the player is on more than one team, this function only returns the first one.
     */
    fun getTeam(player: Player): Team? {
        for (team in teams) {
            if (team.players.contains(player)) return team
        }
        return null
    }

    enum class AutoTeamMode {
        /**
         * Generate as many teams as possible with a specific number of players on each team.
         * Fill each team before starting the next team.
         */
        PLAYER_COUNT,

        /**
         * Generate a specific number of teams with as many players as possible.
         * Use a round robin approach to distribute players evenly.
         */
        TEAM_COUNT
    }

    data class Team(
        val name: Component = Component.empty(),
        val players: MutableList<Player> = mutableListOf(),
        val allowFriendlyFire: Boolean = false
    ) : PacketGroupingAudience {
        override fun getPlayers(): MutableCollection<Player> = players

        override fun toString(): String =
            PlainTextComponentSerializer.plainText().serialize(name) + players.joinToString(
                prefix = "[", postfix = "]"
            ) { it.username }
    }
}