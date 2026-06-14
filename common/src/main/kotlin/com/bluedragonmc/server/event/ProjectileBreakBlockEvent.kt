package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerInstanceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

class ProjectileBreakBlockEvent(
    game: Game,
    private val instance: Instance,
    private val shooter: Player,
    val block: Block,
    val blockPosition: Point
) : GameEvent(game), PlayerInstanceEvent {
    override fun getPlayer() = shooter
    override fun getInstance() = instance
}