package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.gameplay.SpectatorModule
import com.bluedragonmc.server.module.gameplay.TeamModule
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import java.time.Duration

class WinModule(
    val winCondition: WinCondition = WinCondition.MANUAL,
    private val coinAwardsFunction: (Player, TeamModule.Team) -> Int = { _, _ -> 0 },
) : GameModule() {
    private lateinit var parent: Game

    private var winnerDeclared = false

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(SpectatorModule.StartSpectatingEvent::class.java) {
            if (winCondition == WinCondition.MANUAL) return@addListener
            val spectatorModule =
                parent.getModule<SpectatorModule>() // This module is required for all the win conditions
            if (winCondition == WinCondition.LAST_TEAM_ALIVE) {
                val remainingTeams = parent.getModule<TeamModule>().teams.filter { team ->
                    team.players.any { player -> !spectatorModule.isSpectating(player) }
                }
                if (remainingTeams.size == 1) {
                    declareWinner(remainingTeams.first())
                }
            } else if (winCondition == WinCondition.LAST_PLAYER_ALIVE && parent.players.size - spectatorModule.spectatorCount() <= 1) {
                parent.players.firstOrNull { player -> !spectatorModule.isSpectating(player) }?.let { declareWinner(it) }
            }
        }
        eventNode.addListener(WinnerDeclaredEvent::class.java) { event ->
            parent.players.forEach { player ->
                val coins = coinAwardsFunction(player, event.winningTeam)
                if (coins == 0) return@forEach
                parent.getModule<AwardsModule>()
                    .awardCoins(player, coins, if (player in event.winningTeam.players) "Win" else "Participation")
            }
        }
        if (winCondition == WinCondition.TOUCH_EMERALD) eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (!winnerDeclared && event.player.instance?.getBlock(event.player.position.sub(0.0, 1.0, 0.0)) == Block.EMERALD_BLOCK) {
                declareWinner(event.player)
            }
        }
    }

    /**
     * Declares the winner of the game to be a specific team, waits 5 seconds, and ends the game.
     * All players are notified of the winning team.
     */
    fun declareWinner(team: TeamModule.Team) {
        MinecraftServer.getGlobalEventHandler().callCancellable(WinnerDeclaredEvent(parent, team)) {
            winnerDeclared = true
            parent.sendMessage(team.name.append(Component.text(" won the game!", BRAND_COLOR_PRIMARY_2))
                .surroundWithSeparators())
            for (p in parent.players) {
                if (team.players.contains(p)) p.showTitle(Title.title(Component.text("VICTORY!",
                    NamedTextColor.GOLD,
                    TextDecoration.BOLD), Component.empty()))
                else p.showTitle(Title.title(Component.text("GAME OVER!", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Better luck next time!", NamedTextColor.RED)))
            }
            parent.endGame(Duration.ofSeconds(5))
        }
    }

    class WinnerDeclaredEvent(game: Game, val winningTeam: TeamModule.Team) : GameEvent(game)

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
        LAST_TEAM_ALIVE,

        /**
         * Automatically declares the winner as the first player to step on an emerald block.
         */
        TOUCH_EMERALD
    }

}