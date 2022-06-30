package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game
import net.minestom.server.event.trait.CancellableEvent

abstract class GameEvent(val game: Game) : CancellableEvent {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

}

class GameStartEvent(game: Game) : GameEvent(game)