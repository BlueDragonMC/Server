package com.bluedragonmc.games.infinijump.block

import com.bluedragonmc.games.infinijump.InfinijumpGame
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

class IronBarParkourBlock(game: InfinijumpGame, instance: Instance, spawnTime: Long, posIn: Pos) :
    ParkourBlock(game, instance, spawnTime, posIn) {
    override val placedBlockType: Block = Block.IRON_BARS

    override fun create() {
        instance.setBlock(pos, placedBlockType)
    }
}