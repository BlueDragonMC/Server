package com.bluedragonmc.server

import com.bluedragonmc.server.model.GameDocument
import com.bluedragonmc.server.model.InstanceRecord
import com.bluedragonmc.server.model.PlayerRecord
import com.bluedragonmc.server.model.TeamRecord
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.utils.toPlainText
import kotlinx.coroutines.launch
import java.util.*
import kotlin.reflect.jvm.jvmName

/**
 * Collects statistics about a single [Game] and persists them once the game ends.
 */
class GameRecorder(private val game: Game) {

    private var startTime: Date? = null
    private var winningTeam: TeamRecord? = null

    fun recordStart() {
        startTime = Date()
    }

    fun recordWinner(event: WinModule.WinnerDeclaredEvent) {
        winningTeam = TeamRecord(
            event.winningTeamName.toPlainText(),
            event.winningTeamPlayers.map { PlayerRecord(it.uuid, it.username) },
        )
    }

    fun log() {
        val startTime = startTime ?: return

        val statHistory = game.getModuleOrNull<StatisticsModule>()?.getHistory()
        val teams = game.getModuleOrNull<TeamModule>()?.teams?.map { team ->
            TeamRecord(
                name = team.name.toPlainText(),
                players = team.players.map { player ->
                    PlayerRecord(
                        uuid = player.uuid,
                        username = player.username,
                    )
                },
            )
        }

        val instanceRecords = game.getOwnedInstances().map { instance ->
            InstanceRecord(
                type = instance::class.jvmName,
                uuid = instance.uuid,
            )
        }

        Database.IO.launch {
            Database.connection.logGame(
                GameDocument(
                    gameId = game.id,
                    serverId = Environment.getServerName(),
                    gameType = game.data.name,
                    mapName = game.data.mapSource.id,
                    mode = game.data.mode,
                    statistics = statHistory,
                    teams = teams,
                    winningTeam = winningTeam,
                    startTime = startTime,
                    endTime = Date(),
                    instances = instanceRecords,
                )
            )
        }
    }
}
