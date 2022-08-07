package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.CancellableEvent
import net.minestom.server.event.trait.PlayerEvent

abstract class Cancellable : CancellableEvent {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }
}

abstract class CancellablePlayerEvent(private val player: Player) : Cancellable(), PlayerEvent {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getPlayer() = player
}

abstract class GameEvent(val game: Game) : Cancellable()

class GameStartEvent(game: Game) : GameEvent(game)

/**
 * Called when a player leaves the game, either by joining a different game or disconnecting from the server.
 */
class PlayerLeaveGameEvent(game: Game, private val player: Player) : GameEvent(game), PlayerEvent {
    override fun getPlayer() = player
}

/**
 * Can be called by a module when a player has killed another player.
 */
class PlayerKillPlayerEvent(val attacker: Player, val target: Player) : CancellablePlayerEvent(attacker)