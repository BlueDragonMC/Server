package com.bluedragonmc.games.infinijump.block

import com.bluedragonmc.games.infinijump.InfinijumpGame
import com.bluedragonmc.games.infinijump.blocksPerDifficulty
import com.bluedragonmc.server.utils.packet.PacketUtils
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockBreakAnimationPacket
import net.minestom.server.particle.Particle
import net.minestom.server.sound.SoundEvent
import java.time.Duration
import kotlin.random.Random

open class ParkourBlock(val game: InfinijumpGame, val instance: Instance, var spawnTime: Long, posIn: Pos) {

    internal var placedBlockType: Block = Block.AIR

    val pos = Pos(posIn.blockX().toDouble(), posIn.blockY().toDouble(), posIn.blockZ().toDouble())

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
        val p = Pos(pos.blockX().toDouble(), pos.blockY().toDouble(), pos.blockZ().toDouble())
        if (aliveTicks % 10 == 0) {
            sendBlockAnimation(p, (aliveTicks / (game.blockLiveTime / 10)).toByte())
        }

        if (aliveTicks % 10 != 0) return
        val packet = PacketUtils.createParticleWithBlockState(pos.add(Math.random() - 0.5,
            Math.random() - 0.5,
            Math.random() - 0.5), Particle.FALLING_DUST, placedBlockType, 2)
        instance.sendGroupedPacket(packet)
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

    open fun create(block: Block) {
        placedBlockType = block
        instance.setBlock(pos, block)
    }

    open fun destroy() {
        if (isRemoved) return
        isRemoved = true
        instance.setBlock(pos, Block.AIR)
    }

    override fun toString(): String {
        return "ParkourBlock(pos=$pos, isReached=$isReached, isRemoved=$isRemoved)"
    }
}