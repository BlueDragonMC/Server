package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.PlayerKillPlayerEvent
import com.bluedragonmc.server.module.minigame.WinModule
import net.minestom.server.event.player.PlayerDeathEvent

object StatRecorders {
    /**
     * Increments a statistic when a player kills another player.
     * One is added to the attacker's "kills" statistic.
     */
    val PLAYER_KILLS = StatisticsModule.EventStatisticRecorder(PlayerKillPlayerEvent::class.java) { game, event ->
        incrementStatistic(event.attacker, getStatPrefix(game) + "_kills")
    }

    /**
     * Increments a statistic when a player dies by any cause.
     */
    val PLAYER_DEATHS_ALL = StatisticsModule.EventStatisticRecorder(PlayerDeathEvent::class.java) { game, event ->
        incrementStatistic(event.player, getStatPrefix(game) + "_deaths")
    }

    /**
     * Increments a statistic when a player dies because another player killed them in combat.
     * One is added to the target's "deaths_by_player" statistic.
     */
    val PLAYER_DEATHS_BY_PLAYER =
        StatisticsModule.EventStatisticRecorder(PlayerKillPlayerEvent::class.java) { game, event ->
            incrementStatistic(event.target, getStatPrefix(game) + "_deaths_by_player")
        }

    /**
     * Combines the following:
     * [PLAYER_KILLS], [PLAYER_DEATHS_ALL], [PLAYER_DEATHS_BY_PLAYER]
     */
    val KILLS_AND_DEATHS =
        StatisticsModule.MultiStatisticRecorder(PLAYER_KILLS, PLAYER_DEATHS_ALL, PLAYER_DEATHS_BY_PLAYER)

    /**
     * Records a "wins" and a "losses" statistic when a winner is declared.
     */
    val WINS_AND_LOSSES =
        StatisticsModule.EventStatisticRecorder(WinModule.WinnerDeclaredEvent::class.java) { game, event ->
            game.players.forEach { player ->
                if (player in event.winningTeam.players) {
                    incrementStatistic(player, getStatPrefix(game) + "_wins")
                } else {
                    incrementStatistic(player, getStatPrefix(game) + "_losses")
                }
            }
        }

    val ALL = StatisticsModule.MultiStatisticRecorder(
        KILLS_AND_DEATHS, WINS_AND_LOSSES
    )

    private fun getStatPrefix(game: Game): String {
        return if (game.mode.isNullOrBlank()) "game_${game.name.lowercase()}_wins"
        else "game_${game.name.lowercase()}_${game.mode.lowercase()}_wins"
    }
}