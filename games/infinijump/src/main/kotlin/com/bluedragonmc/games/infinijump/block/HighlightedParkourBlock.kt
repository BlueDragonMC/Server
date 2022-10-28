package com.bluedragonmc.games.infinijump.block

import com.bluedragonmc.games.infinijump.InfinijumpGame
import com.bluedragonmc.server.utils.packet.GlowingEntityUtils
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance

open class HighlightedParkourBlock(game: InfinijumpGame, instance: Instance, spawnTime: Long, posIn: Pos) :
    ParkourBlock(game, instance, spawnTime, posIn) {

    val entity = Entity(EntityType.SHULKER)

    override fun create() {
        super.create()
        entity.entityMeta.apply {
            isInvisible = true
            isHasNoGravity = true
            isSilent = true
        }
        entity.setInstance(instance, pos)
    }

    override fun tick(aliveTicks: Int) {
        super.tick(aliveTicks)
        val color = when {
            isReached -> NamedTextColor.WHITE
            aliveTicks < (game.blockLiveTime / 3) -> NamedTextColor.GREEN
            aliveTicks < (2 * game.blockLiveTime / 3) -> NamedTextColor.YELLOW
            else -> NamedTextColor.RED
        }
        setColor(color)
    }

    protected open fun setColor(color: NamedTextColor) {
        GlowingEntityUtils.glow(entity, color, entity.viewers)
    }

    override fun destroy() {
        super.destroy()
        entity.remove()
    }

}