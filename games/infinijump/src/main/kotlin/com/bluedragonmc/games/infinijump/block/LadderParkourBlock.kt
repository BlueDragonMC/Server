package com.bluedragonmc.games.infinijump.block

import com.bluedragonmc.games.infinijump.InfinijumpGame
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

class LadderParkourBlock(game: InfinijumpGame, instance: Instance, spawnTime: Long, posIn: Pos) :
    HighlightedParkourBlock(game, instance, spawnTime, posIn) {

    init {
        instance.setBlock(pos.sub(1.0, 0.0, 0.0), Block.LADDER.withProperty("facing", "west"))
        instance.setBlock(pos.sub(0.0, 0.0, 1.0), Block.LADDER.withProperty("facing", "north"))
        instance.setBlock(pos.add(1.0, 0.0, 0.0), Block.LADDER.withProperty("facing", "east"))
        instance.setBlock(pos.add(0.0, 0.0, 1.0), Block.LADDER.withProperty("facing", "south"))
    }

    override fun destroy() {
        super.destroy()
        setNeighboringBlocks(Block.AIR)
    }
}