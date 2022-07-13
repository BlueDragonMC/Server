package com.bluedragonmc.server.queue

import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.game.BedWarsGame
import com.bluedragonmc.server.game.TeamDeathmatchGame
import com.bluedragonmc.server.game.WackyMazeGame
import net.minestom.server.entity.Player

abstract class Queue {

    internal val gameClasses = hashMapOf(
        "WackyMaze" to ::WackyMazeGame,
        "TeamDeathmatch" to ::TeamDeathmatchGame,
        "BedWars" to ::BedWarsGame,
    )

    abstract fun start()
    abstract fun queue(player: Player, gameType: GameType)
}