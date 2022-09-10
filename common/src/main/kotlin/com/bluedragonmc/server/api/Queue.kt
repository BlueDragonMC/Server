package com.bluedragonmc.server.api

import com.bluedragonmc.messages.GameType
import net.minestom.server.entity.Player

abstract class Queue {

    abstract fun start()
    abstract fun queue(player: Player, gameType: GameType)
}