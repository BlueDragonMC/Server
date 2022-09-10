package com.bluedragonmc.server.event

import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerEvent

/**
 * An event which is cancellable and player-specific.
 */
abstract class CancellablePlayerEvent(private val player: Player) : Cancellable(), PlayerEvent {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getPlayer() = player
}