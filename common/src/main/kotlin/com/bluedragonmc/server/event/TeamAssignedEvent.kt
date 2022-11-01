package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerEvent

class TeamAssignedEvent(game: Game, private val player: Player) : GameEvent(game), PlayerEvent {
    override fun getPlayer(): Player = player
}