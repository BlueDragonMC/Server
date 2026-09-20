package com.bluedragonmc.server.event

import com.bluedragonmc.server.*
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerEvent

/**
 * Called when a player leaves the game, either by joining a different game or disconnecting from the server.
 */
class PlayerLeaveGameEvent(game: ModuleHolder, private val player: Player) : GameEvent(game), PlayerEvent {
    override fun getPlayer() = player
}