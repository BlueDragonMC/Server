package com.bluedragonmc.games.infinijump.block

import com.bluedragonmc.games.infinijump.InfinijumpGame
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

class PlatformParkourBlock(game: InfinijumpGame, instance: Instance, spawnTime: Long, posIn: Pos) :
    ParkourBlock(game, instance, spawnTime, posIn) {
    override val placedBlockType: Block = Block.RED_CONCRETE
    override val fallingBlockType: Block? = Block.BARRIER

    override fun create() {
        super.create()
        setNeighboringBlocks(Block.RED_CONCRETE, corners = true)
    }

    override fun tick(aliveTicks: Int) {
        super.tick(aliveTicks)
        if (aliveTicks % 10 == 0) {
            forEachNeighboringBlock(corners = true) { pos ->
                sendBlockAnimation(pos, (aliveTicks / (game.blockLiveTime / 10)).toByte())
            }
        }
    }

    override fun destroy() {
        super.destroy()
        setNeighboringBlocks(Block.AIR, corners = true)
    }
}