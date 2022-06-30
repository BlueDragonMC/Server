package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.gameplay.SpectatorModule
import com.bluedragonmc.server.module.gameplay.TeamModule
import com.bluedragonmc.server.utils.SingleAssignmentProperty
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.time.Duration

class WinModule(val winCondition: WinCondition = WinCondition.MANUAL) : GameModule() {
    private var parent by SingleAssignmentProperty<Game>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(SpectatorModule.StartSpectatingEvent::class.java) {
            if (winCondition == WinCondition.MANUAL) return@addListener
            val spectatorModule = parent.getModule<SpectatorModule>() // This module is required for all the win conditions
            if (winCondition == WinCondition.LAST_TEAM_ALIVE) {
                val teamModule = parent.getModule<TeamModule>()
                var remainingTeam: TeamModule.Team? = null
                for (team in teamModule.teams) {
                    var teamIsRemaining = false
                    for (player in team.players) {
                        if (!spectatorModule.isSpectating(player)) {
                            teamIsRemaining = true
                        }
                        if (teamIsRemaining) {
                            if (remainingTeam == null) remainingTeam = team
                            else return@addListener // if it gets to this point, there is more than 1 remaining team
                        }
                    }
                    if (remainingTeam != null) declareWinner(remainingTeam)
                }
            } else if (winCondition == WinCondition.LAST_PLAYER_ALIVE && parent.players.size - spectatorModule.spectatorCount() <= 1) {
                for (player in parent.players) {
                    if (!spectatorModule.isSpectating(player)) {
                        declareWinner(player)
                        break
                    }
                }
            }
        }
    }

    /**
     * Declares the winner of the game to be a specific team, waits 5 seconds, and ends the game.
     * All players are notified of the winning team.
     */
    fun declareWinner(team: TeamModule.Team) {
        parent.sendMessage(
            team.name.append(
                Component.text(
                    " won the game!",
                    NamedTextColor.DARK_AQUA
                )
            ).surroundWithSeparators()
        )
        for (p in parent.players) {
            if (team.players.contains(p)) p.showTitle(
                Title.title(
                    Component.text("VICTORY!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.empty()
                )
            )
            else p.showTitle(
                Title.title(
                    Component.text("GAME OVER!", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Better luck next time!", NamedTextColor.RED)
                )
            )
        }
        parent.endGame(Duration.ofSeconds(5))
    }

    fun declareWinner(winner: Component) {
        declareWinner(TeamModule.Team(winner, mutableListOf()))
    }

    fun declareWinner(winner: Player) {
        declareWinner(TeamModule.Team(winner.name, mutableListOf(winner)))
    }

    enum class WinCondition {
        /**
         * No automatic win condition. Use the `declareWinner` function to manually declare the winner.
         */
        MANUAL,

        /**
         * Automatically declare the winner as the last non-spectating player. Requires the `SpectatorModule` to be active.
         */
        LAST_PLAYER_ALIVE,

        /**
         * Automatically declares the winner as the last team to have a non-spectating player. Requires the `SpectatorModule` and `TeamModule` to be active.
         */
        LAST_TEAM_ALIVE
    }

}