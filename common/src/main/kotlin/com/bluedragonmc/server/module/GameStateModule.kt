package com.bluedragonmc.server.module

import com.bluedragonmc.server.GameLifecycle
import com.bluedragonmc.server.ModuleHolder
import com.bluedragonmc.server.utils.GameState
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.time.Duration

class GameStateModule(private val lifecycle: GameLifecycle) : GameModule() {

    var state: GameState
        get() = lifecycle.state
        set(value) {
            lifecycle.state = value
        }

    fun endGame(queueAllPlayers: Boolean = true) = lifecycle.end(queueAllPlayers)

    fun endGameLater(delay: Duration = Duration.ZERO) = lifecycle.endLater(delay)

    override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {}
}
