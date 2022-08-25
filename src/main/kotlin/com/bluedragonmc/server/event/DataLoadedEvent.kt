package com.bluedragonmc.server.event

import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerEvent

/**
 * Called when a player's data document has been fetched from the database.
 * Will always be called after AsyncPlayerPreLoginEvent, and the [player]
 * will always be an instance of `CustomPlayer` with an initialized `data` field.
 */
class DataLoadedEvent(private val player: Player) : PlayerEvent {
    override fun getPlayer(): Player = player
}