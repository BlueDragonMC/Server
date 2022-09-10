package com.bluedragonmc.games.infinijump.block

import com.bluedragonmc.games.infinijump.InfinijumpGame
import com.bluedragonmc.games.infinijump.blocksPerDifficulty
import com.bluedragonmc.server.utils.packet.GlowingEntityUtils
import com.bluedragonmc.server.utils.packet.PacketUtils
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.FallingBlockMeta
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockBreakAnimationPacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import kotlin.random.Random

open class ParkourBlock(val game: InfinijumpGame, val instance: Instance, var spawnTime: Long, posIn: Pos) {

    /**
     * Setting the placed block type will determine whether the breaking animation is visible.
     * Placed barriers have no breaking animation.
     */
    internal open val placedBlockType: Block = Block.STONE_BRICKS

    /**
     *
     * Setting the falling block type will determine the shape of the block's glow effect.
     * Barriers and invisible falling blocks have no glowing effect.
     */
    protected open val fallingBlockType: Block? = Block.INFESTED_STONE_BRICKS

    val pos = Pos(posIn.blockX().toDouble(), posIn.blockY().toDouble(), posIn.blockZ().toDouble())

    private val centerBottom = Pos(pos.blockX().toDouble() + 0.5,
        pos.blockY().toDouble(),
        pos.blockZ().toDouble() + 0.5) // In the center of the block on the X and Z axes
    private val center = centerBottom.add(0.0, 0.5, 0.0) // In the center of the block on all axes

    val entity = Entity(EntityType.FALLING_BLOCK)

    var isReached = false
    var isRemoved = false

    fun markReached(player: Player, show: Boolean = true) {
        if (isReached) return
        isReached = true
        game.score++
        val packet = PacketUtils.createBlockParticle(player.position, placedBlockType, 10)
        if (show) {
            val sound = Sound.sound(when (game.difficulty) {
                0 -> SoundEvent.BLOCK_NOTE_BLOCK_PLING
                1 -> SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP
                2 -> SoundEvent.BLOCK_AMETHYST_BLOCK_BREAK
                3 -> SoundEvent.BLOCK_BASALT_BREAK
                else -> SoundEvent.BLOCK_NOTE_BLOCK_PLING
            },
                Sound.Source.BLOCK,
                1.0f,
                if (game.score >= 4 * blocksPerDifficulty) 2.0f else 0.5f + (game.score % blocksPerDifficulty) * (1.5f / blocksPerDifficulty) // Max pitch = 2
            )
            player.playSound(sound)
            player.sendPacket(packet)

            player.showTitle(Title.title(Component.empty(),
                Component.text(game.score, NamedTextColor.GOLD),
                Title.Times.times(Duration.ZERO, Duration.ZERO, Duration.ofSeconds(60))))
        }

        MinecraftServer.getSchedulerManager().scheduleNextTick { game.addNewBlock() }
    }

    open fun tick(aliveTicks: Int) {
        val color = when {
            isReached -> NamedTextColor.WHITE
            aliveTicks < (game.blockLiveTime / 3) -> NamedTextColor.GREEN
            aliveTicks < (2 * game.blockLiveTime / 3) -> NamedTextColor.YELLOW
            else -> NamedTextColor.RED
        }
        val p = Pos(pos.blockX().toDouble(), pos.blockY().toDouble(), pos.blockZ().toDouble())
        if (aliveTicks % 10 == 0) {
            sendBlockAnimation(p, (aliveTicks / (game.blockLiveTime / 10)).toByte())
        }
        setOutlineColor(color)

        if (aliveTicks % 10 != 0) return
        val packet = PacketUtils.createParticleWithBlockState(pos.add(Math.random() - 0.5,
            Math.random() - 0.5,
            Math.random() - 0.5), Particle.FALLING_DUST, placedBlockType, 2)
        instance.sendGroupedPacket(packet)
    }

    private fun setOutlineColor(color: NamedTextColor) {
        GlowingEntityUtils.glow(entity, color, entity.viewers)
    }

    protected fun sendBlockAnimation(pos: Pos, progress: Byte) {
        instance.getChunkAt(pos)?.sendPacketToViewers(BlockBreakAnimationPacket(Random.nextInt(), pos, progress))
    }

    protected fun setNeighboringBlocks(block: Block, corners: Boolean = false) =
        forEachNeighboringBlock(corners = corners) { pos ->
            instance.setBlock(pos, block)
        }

    protected inline fun forEachNeighboringBlock(corners: Boolean = false, block: (Pos) -> Unit) {
        block(pos.add(1.0, 0.0, 0.0))
        block(pos.add(0.0, 0.0, 1.0))
        block(pos.sub(1.0, 0.0, 0.0))
        block(pos.sub(0.0, 0.0, 1.0))

        if (corners) {
            block(pos.add(1.0, 0.0, 1.0))
            block(pos.add(1.0, 0.0, -1.0))
            block(pos.add(-1.0, 0.0, 1.0))
            block(pos.add(-1.0, 0.0, -1.0))
        }
    }

    open fun create() {
        instance.setBlock(pos, placedBlockType)
        if (fallingBlockType != null) (entity.entityMeta as FallingBlockMeta).apply {
            isInvisible = false
            isHasNoGravity = true
            block = fallingBlockType!!
        }
        entity.setInstance(instance, centerBottom)
    }

    open fun destroy() {
        if (isRemoved) return
        isRemoved = true
        instance.setBlock(pos, Block.AIR)
        entity.remove()
    }
}