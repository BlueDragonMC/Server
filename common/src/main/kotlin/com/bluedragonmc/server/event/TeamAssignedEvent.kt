package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.minigame.TeamModule
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerEvent

class TeamAssignedEvent(game: Game, private val player: Player, val team: TeamModule.Team) : GameEvent(game), PlayerEvent {
    override fun getPlayer(): Player = player
}