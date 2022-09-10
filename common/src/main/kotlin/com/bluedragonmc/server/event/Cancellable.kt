package com.bluedragonmc.server.event

import net.minestom.server.event.trait.CancellableEvent

abstract class Cancellable : CancellableEvent {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }
}