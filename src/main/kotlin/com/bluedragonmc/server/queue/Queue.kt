package com.bluedragonmc.server.queue

import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.game.*
import net.minestom.server.entity.Player

abstract class Queue {

    companion object {
        internal val gameClasses = hashMapOf(
            "WackyMaze" to ::WackyMazeGame,
//            "TeamDeathmatch" to ::TeamDeathmatchGame,
            "BedWars" to ::BedWarsGame,
            "SkyWars" to ::SkyWarsGame,
            "FastFall" to ::FastFallGame,
            "Infection" to ::InfectionGame,
            "Infinijump" to ::InfinijumpGame,
            "PvPMaster" to ::PvpMasterGame,
        )
    }

    abstract fun start()
    abstract fun queue(player: Player, gameType: GameType)
}