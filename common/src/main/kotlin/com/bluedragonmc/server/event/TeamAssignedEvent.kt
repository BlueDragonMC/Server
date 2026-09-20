package com.bluedragonmc.server.event

import com.bluedragonmc.server.*
import com.bluedragonmc.server.module.minigame.TeamModule
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerEvent

class TeamAssignedEvent(game: ModuleHolder, private val player: Player, val team: TeamModule.Team) : GameEvent(game), PlayerEvent {
    override fun getPlayer(): Player = player
}