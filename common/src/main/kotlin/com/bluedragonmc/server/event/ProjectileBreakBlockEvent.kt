package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.instance.block.Block

class ProjectileBreakBlockEvent(
    game: Game,
    private val shooter: Player,
    val projectile: Entity,
    val block: Block,
    val blockPosition: Point
) : GameEvent(game), PlayerEvent {
    override fun getPlayer() = shooter
}