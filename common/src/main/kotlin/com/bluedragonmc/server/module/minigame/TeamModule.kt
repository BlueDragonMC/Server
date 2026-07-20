package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.event.TeamAssignedEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.minigame.TeamModule.Team
import com.bluedragonmc.server.utils.toPlainText
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.MinecraftServer
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.color.TeamColor
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.network.packet.server.play.TeamsPacket.NameTagVisibility
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

/**
 * A module that provides team support.
 * This module can automatically generate teams when the game starts, or teams can be created manually and added to the `teams` list.
 *
 * [See Documentation](https://developer.bluedragonmc.com/modules/teammodule/)
 */
class TeamModule(
    private val autoTeams: Boolean = false,
    private val autoTeamMode: AutoTeamMode = AutoTeamMode.PLAYER_COUNT,
    private val autoTeamCount: Int = 2,
    private val allowFriendlyFire: Boolean = false
) : GameModule() {
    private lateinit var parent: Game
    private val _teams = mutableListOf<Team>()
    val teams: Collection<Team> = _teams
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(GameStartEvent::class.java) {
            // Auto team system
            if (autoTeams) {
                logger.debug("Splitting ${parent.players.size} players into teams using strategy $autoTeamMode")
                when (autoTeamMode) {
                    AutoTeamMode.PLAYER_COUNT -> {
                        var teamNumber = 0
                        parent.players.chunked(autoTeamCount) { players ->
                            val team = addTeam(teamNumToName(teamNumber++), allowFriendlyFire)
                            players.forEach { player -> team.addPlayer(player) }
                        }
                        logger.info("Created ${_teams.size} teams with $autoTeamCount players per team.")
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
                            val team = addTeam(teamNumToName(i), allowFriendlyFire)
                            players.forEach { player -> team.addPlayer(player) }
                        }
                        logger.info("Created ${_teams.size} teams with $playersPerTeam players per team.")
                    }
                }
            } else logger.debug("Automatic team creation is disabled.")

            logger.debug(_teams.toString())
        }
        eventNode.addListener(OldCombatModule.PlayerAttackEvent::class.java) { event ->
            if (event.target is Player) {
                // Check for friendly fire
                // This listener will only fire if a misbehaving client attacks a player on its team (or a thrown projectile hits a player)
                if (event.attacker == event.target) return@addListener // Allow players to shoot themselves with projectiles
                val attackerTeam = _teams.find { it.players.contains(event.entity) } ?: return@addListener
                if (attackerTeam.allowFriendlyFire) return@addListener
                val targetTeam = _teams.find { it.players.contains(event.target) } ?: return@addListener
                if (attackerTeam == targetTeam) event.isCancelled = true
            }
        }
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            // Remove the player from their scoreboard team when they leave
            getTeam(event.player)?.removePlayer(event.player)
        }
    }

    override fun deinitialize() {
        // Remove all scoreboard teams when the game ends
        _teams.forEach { team ->
            team.players.forEach { player -> team.removePlayer(player) }
            team.unregister()
        }
    }

    /**
     * Converts a team number into a color.
     * An easy way to name a team.
     */
    private fun teamNumToName(num: Int): Component {
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
        for (team in _teams) {
            if (team.players.contains(player)) return team
        }
        return null
    }

    /**
     * Returns the team with the specified name color, or null if no team exists with this name color.
     * Note: if more than one team exists with this name color, this function only returns the first one.
     */
    fun getTeam(color: TextColor): Team? {
        for (team in _teams) {
            if (team.name.color() == color) return team
        }
        return null
    }

    fun addTeam(
        name: Component = Component.empty(),
        allowFriendlyFire: Boolean = false,
        nameTagVisibility: NameTagVisibility = NameTagVisibility.ALWAYS,
        collisionRule: TeamsPacket.CollisionRule = TeamsPacket.CollisionRule.ALWAYS
    ): Team {
        val team = Team(name, allowFriendlyFire, nameTagVisibility, collisionRule)
        _teams.add(team)
        team.register()
        return team
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


    inner class Team internal constructor(
        val name: Component = Component.empty(),
        val allowFriendlyFire: Boolean = false,
        val nameTagVisibility: NameTagVisibility = NameTagVisibility.ALWAYS,
        val collisionRule: TeamsPacket.CollisionRule = TeamsPacket.CollisionRule.ALWAYS,
    ) : PacketGroupingAudience {
        val uuid: UUID = UUID.randomUUID()
        private val _players = CopyOnWriteArraySet<Player>()

        lateinit var scoreboardTeam: net.minestom.server.scoreboard.Team
            private set


        override fun getPlayers(): Collection<Player> = _players

        fun addPlayer(player: Player, sendChatMessage: Boolean = true) {
            require(getTeam(player) == null) { "Player must not already be on a team!" }
            eventNode.call(TeamAssignedEvent(parent, player, this))
            _players.add(player)
            if (::scoreboardTeam.isInitialized)
                scoreboardTeam.addMember(player.username)

            (player as CustomPlayer).updateDisplayName(name.color())
            if (sendChatMessage) {
                player.sendMessage(Component.translatable("module.team.assignment", NamedTextColor.GREEN, name))
            }
        }

        fun removePlayer(player: Player) {
            _players.remove(player)
            if (::scoreboardTeam.isInitialized)
                scoreboardTeam.removeMember(player.username)

            (player as CustomPlayer).updateDisplayName(null)
        }

        override fun toString(): String =
            PlainTextComponentSerializer.plainText().serialize(name) + players.joinToString(
                prefix = "[", postfix = "]"
            ) { it.username }

        /**
         * Create a scoreboard team and register it with the Minestom server.
         */
        internal fun register() {
            val builder = MinecraftServer.getTeamManager()
                .createBuilder(uuid.toString())
                .teamDisplayName(name)
                .prefix(
                    Component.text(
                        name.toPlainText().first() + " ", // The first letter of the team's name
                        name.color() ?: NamedTextColor.WHITE, // The team name's text color
                        TextDecoration.BOLD
                    )
                )
                .nameTagVisibility(nameTagVisibility)
                .collisionRule(collisionRule)
                .teamColor(
                    TeamColor.fromName(
                        NamedTextColor.nearestTo(
                            name.color() ?: NamedTextColor.WHITE
                        ).name()
                    )
                ) // Used for coloring player usernames
            // Allow friendly fire if necessary
            if (allowFriendlyFire) builder.allowFriendlyFire()
            scoreboardTeam = builder.build() // Register the team
            players.forEach { player ->
                // Add members to the team
                scoreboardTeam.addMember(player.username)
            }
        }

        /**
         * Removes the scoreboard team from the Minestom server, if it was previously initialized.
         */
        fun unregister() {
            if (::scoreboardTeam.isInitialized) {
                MinecraftServer.getTeamManager().deleteTeam(scoreboardTeam)
            }
        }
    }
}

val Team.players get() = getPlayers()