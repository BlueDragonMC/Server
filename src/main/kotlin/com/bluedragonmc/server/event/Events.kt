package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.CancellableEvent
import net.minestom.server.event.trait.PlayerEvent

abstract class GameEvent(val game: Game) : CancellableEvent {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

}

class GameStartEvent(game: Game) : GameEvent(game)

/**
 * Called when a player leaves the game, either by joining a different game or disconnecting from the server.
 */
class PlayerLeaveGameEvent(game: Game, private val p: Player) : GameEvent(game), PlayerEvent {
    override fun getPlayer(): Player {
        return p
    }

}