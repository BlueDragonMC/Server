package com.bluedragonmc.server.event

import com.bluedragonmc.server.GameContext
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerEvent

/**
 * Called when a player joins the game.
 */
class PlayerJoinGameEvent(private val player: Player, game: GameContext) : GameEvent(game), PlayerEvent {
    override fun getPlayer(): Player = player
}