package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace

class ProjectileBreakBlockEvent(
    game: Game,
    private val shooter: Player,
    val block: Block,
    val blockPosition: Point,
    val blockFace: BlockFace
) : GameEvent(game), PlayerEvent {
    override fun getPlayer() = shooter
}