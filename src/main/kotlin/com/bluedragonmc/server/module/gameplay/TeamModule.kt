package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

/**
 * A module that provides team support.
 * This module can automatically generate teams when the game starts, or teams can be created manually and added to the `teams` list.
 */
class TeamModule(val autoTeams: Boolean = false, val autoTeamMode: AutoTeamMode = AutoTeamMode.PLAYER_COUNT, val autoTeamCount: Int = 2) : GameModule() {
    val teams = mutableListOf<Team>()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(GameStartEvent::class.java) {
            // Auto team system
            if (autoTeams) {
                if (autoTeamMode == AutoTeamMode.PLAYER_COUNT) { // Set number of players on each team
                    var i = 0
                    // Each iteration of this outer loop creates a new team
                    while (i < parent.players.size) {
                        val teamPlayers = mutableListOf<Player>()
                        var j = 0
                        // Each iteration of this inner loop adds a player to the team
                        while (j < autoTeamCount) {
                            if (i + j >= parent.players.size) break // No more players to add to any team
                            teamPlayers.add(parent.players[i + j])
                            j++
                        }
                        teams.add(Team(teamNumToName(i / autoTeamCount), teamPlayers))
                        i += autoTeamCount
                    }
                } else { // Set number of teams
                    var team = 0
                    // Create teams
                    for (i in 0 until autoTeamCount) teams.add(Team(teamNumToName(i), mutableListOf()))
                    // Add players to teams
                    for (player in parent.players) {
                        if (team >= autoTeamCount) team = 0
                        teams[team].players.add(player)
                        player.sendMessage(Component.text("You are on ").append(teams[team].name))
                        team++
                    }
                }
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
    data class Team(val name: Component, val players: MutableList<Player>)
}