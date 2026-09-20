package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.event.PlayerKillPlayerEvent
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.GameState
import net.minestom.server.event.player.PlayerDeathEvent

object StatRecorders {
    /**
     * Increments a statistic when a player kills another player.
     * One is added to the attacker's "kills" statistic.
     */
    val PLAYER_KILLS = StatisticsModule.EventStatisticRecorder(PlayerKillPlayerEvent::class.java) { event ->
        incrementStatistic(event.attacker, statPrefix() + "_kills")
    }

    /**
     * Increments a statistic when a player dies by any cause.
     */
    val PLAYER_DEATHS_ALL = StatisticsModule.EventStatisticRecorder(PlayerDeathEvent::class.java) { event ->
        if (state == GameState.INGAME) {
            incrementStatistic(event.player, statPrefix() + "_deaths")
        }
    }

    /**
     * Increments a statistic when a player dies because another player killed them in combat.
     * One is added to the target's "deaths_by_player" statistic.
     */
    val PLAYER_DEATHS_BY_PLAYER =
        StatisticsModule.EventStatisticRecorder(PlayerKillPlayerEvent::class.java) { event ->
            incrementStatistic(event.target, statPrefix() + "_deaths_by_player")
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
        StatisticsModule.EventStatisticRecorder(WinModule.WinnerDeclaredEvent::class.java) { event ->
            ArrayList(players).forEach { player ->
                if (player in event.winningTeamPlayers) {
                    incrementStatistic(player, statPrefix() + "_wins")
                } else {
                    incrementStatistic(player, statPrefix() + "_losses")
                }
            }
        }

    val ALL = StatisticsModule.MultiStatisticRecorder(
        KILLS_AND_DEATHS, WINS_AND_LOSSES
    )

    private fun StatisticsModule.statPrefix(): String {
        val gameData = data
        val mode = gameData.mode
        return if (mode.isNullOrBlank()) "game_${gameData.name.lowercase()}"
        else "game_${gameData.name.lowercase()}_${mode.lowercase()}"
    }
}
