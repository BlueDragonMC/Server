package com.bluedragonmc.server.api

import com.bluedragonmc.messages.GameType
import net.minestom.server.entity.Player
import java.io.File

abstract class Queue {

    abstract fun start()
    abstract fun queue(player: Player, gameType: GameType)

    abstract fun getMaps(gameType: String): Array<File>?
    abstract fun randomMap(gameType: String): String?
}