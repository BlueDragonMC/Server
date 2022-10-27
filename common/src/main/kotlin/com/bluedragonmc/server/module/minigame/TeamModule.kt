package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.utils.toPlainText
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
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
    private val allowFriendlyFire: Boolean = false,
    private val teamsAutoAssignedCallback: () -> Unit = {}
) : GameModule() {
    val teams = mutableListOf<Team>()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(GameStartEvent::class.java) {
            // Auto team system
            if (autoTeams) {
                logger.debug("Splitting ${parent.players.size} players into teams using strategy $autoTeamMode")
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
                        // The players per team is rounded, so some teams might have to be larger than the optimal
                        // amount of players per team. For example, if there are 10 players and 3 teams, there must
                        // be one team with 4 players instead of 3 in order to keep the number of teams constant.
                        var compensation = parent.players.size - playersPerTeam * teamCount
                        var rollingIndex = 0
                        for (i in 0 until teamCount) {
                            val startIndex = rollingIndex // Start at the current index
                            rollingIndex += playersPerTeam // End at the current index plus the amount of players per team
                            if (compensation > 0) {
                                rollingIndex++ // If we need to compensate, add another player to this team
                                compensation-- // Decrement the number of players we have to compensate for
                            }
                            rollingIndex = rollingIndex.coerceAtMost(parent.players.size)
                            val players = parent.players.subList(startIndex, rollingIndex)
                            teams.add(Team(teamNumToName(i), players, allowFriendlyFire))
                        }
                        logger.info("Created ${teams.size} teams with $playersPerTeam players per team.")
                    }
                }
                teamsAutoAssignedCallback()
            } else logger.debug("Automatic team creation is disabled.")

            logger.debug(teams.toString())

            teams.forEach { team ->
                team.players.forEach { it.sendMessage(Component.translatable("module.team.assignment", NamedTextColor.GREEN, team.name)) }
                val builder = MinecraftServer.getTeamManager()
                    .createBuilder(parent.getInstance().uniqueId.toString() + "-" + team.name.toPlainText())
                    .teamDisplayName(team.name)
                    .prefix(Component.text(
                        team.name.toPlainText().first() + " ", // The first letter of the team's name
                        team.name.color() ?: NamedTextColor.WHITE, // The team name's text color
                        TextDecoration.BOLD
                    ))
                    .teamColor(NamedTextColor.nearestTo(
                        team.name.color() ?: NamedTextColor.WHITE
                    )) // Used for coloring player usernames
                // Allow friendly fire if necessary
                if (allowFriendlyFire) builder.allowFriendlyFire()
                val scoreboardTeam = builder.build() // Register the team
                team.players.forEach { player ->
                    // Add members to the team
                    scoreboardTeam.addMember(player.username)
                }
                team.scoreboardTeam = scoreboardTeam
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
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            // Remove the player from their scoreboard team when they leave
            event.player.team?.removeMember(event.player.username)
        }
    }

    override fun deinitialize() {
        // Remove all scoreboard teams when the game ends
        teams.forEach { team ->
            if (team.hasScoreboardTeam()) {
                MinecraftServer.getTeamManager().deleteTeam(team.scoreboardTeam)
            }
        }
    }

    /**
     * Converts a team number into a color.
     * An easy way to name a team.
     */
    fun teamNumToName(num: Int): Component {
        return when (num) {
            0 -> Component.text("Red", NamedTextColor.RED)
            1 -> Component.text("Blue", NamedTextColor.BLUE)
            2 -> Component.text("Green", NamedTextColor.GREEN)
            3 -> Component.text("Aqua", NamedTextColor.AQUA)
            4 -> Component.text("Pink", NamedTextColor.LIGHT_PURPLE)
            5 -> Component.text("White", NamedTextColor.WHITE)
            6 -> Component.text("Gray", NamedTextColor.GRAY)
            7 -> Component.text("Yellow", NamedTextColor.YELLOW)
            8 -> Component.text("Gold", NamedTextColor.GOLD)
            9 -> Component.text("Purple", NamedTextColor.DARK_PURPLE)
            10 -> Component.text("Dark Green", NamedTextColor.DARK_GREEN)
            11 -> Component.text("Dark Aqua", NamedTextColor.DARK_AQUA)
            12 -> Component.text("Dark Red", NamedTextColor.DARK_RED)
            13 -> Component.text("Dark Gray", NamedTextColor.DARK_GRAY)
            14 -> Component.text("Dark Blue", NamedTextColor.DARK_BLUE)
            else -> Component.text("Team $num", NamedTextColor.AQUA)
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

    /**
     * Returns the team with the specified name color, or null if no team exists with this name color.
     * Note: if more than one team exists with this name color, this function only returns the first one.
     */
    fun getTeam(color: TextColor): Team? {
        for (team in teams) {
            if (team.name.color() == color) return team
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
         * Use a round-robin approach to distribute players evenly.
         */
        TEAM_COUNT
    }

    data class Team(
        val name: Component = Component.empty(),
        val players: MutableList<Player> = mutableListOf(),
        val allowFriendlyFire: Boolean = false
    ) : PacketGroupingAudience {
        lateinit var scoreboardTeam: net.minestom.server.scoreboard.Team

        fun hasScoreboardTeam() = ::scoreboardTeam.isInitialized

        override fun getPlayers(): MutableCollection<Player> = players

        fun addPlayer(player: Player) {
            players.add(player)
            scoreboardTeam.addMember(player.username)
        }

        override fun toString(): String =
            PlainTextComponentSerializer.plainText().serialize(name) + players.joinToString(
                prefix = "[", postfix = "]"
            ) { it.username }
    }
}