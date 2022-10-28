package com.bluedragonmc.games.infinijump.block

import com.bluedragonmc.games.infinijump.InfinijumpGame
import com.bluedragonmc.server.utils.packet.GlowingEntityUtils
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

class PlatformParkourBlock(game: InfinijumpGame, instance: Instance, spawnTime: Long, posIn: Pos) :
    HighlightedParkourBlock(game, instance, spawnTime, posIn) {

    private val extraEntities = mutableListOf<Entity>()

    override fun create(block: Block) {
        super.create(block)
        setNeighboringBlocks(block, corners = true)
        forEachNeighboringBlock(corners = true) { neighborPos ->
            val entity = Entity(this.entity.entityType)
            entity.entityMeta.isInvisible = true
            entity.entityMeta.isHasNoGravity = true
            entity.setInstance(instance, neighborPos)
            extraEntities.add(entity)
        }
    }

    override fun tick(aliveTicks: Int) {
        super.tick(aliveTicks)
        if (aliveTicks % 10 == 0) {
            forEachNeighboringBlock(corners = true) { pos ->
                sendBlockAnimation(pos, (aliveTicks / (game.blockLiveTime / 10)).toByte())
            }
        }
    }

    override fun setColor(color: NamedTextColor) {
        super.setColor(color)
        extraEntities.forEach { e ->
            GlowingEntityUtils.glow(e, color, entity.viewers)
        }
    }

    override fun destroy() {
        extraEntities.forEach(Entity::remove)
        super.destroy()
        setNeighboringBlocks(Block.AIR, corners = true)
    }
}