package com.bluedragonmc.server.utils.packet

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket
import net.minestom.server.particle.Particle
import net.minestom.server.particle.ParticleCreator
import net.minestom.server.utils.binary.BinaryWriter
import java.util.function.Consumer

object PacketUtils {
    fun getRelativePosLookPacket(player: Player, position: Pos) = PlayerPositionAndLookPacket(
        position.withView(0.0f, 0.0f),
        (0x08 or 0x10).toByte(), // flags - see https://wiki.vg/Protocol#Synchronize_Player_Position
        player.nextTeleportId
    )

    fun createParticlePacket(pos: Pos, type: Particle, count: Int): ParticlePacket =
        ParticleCreator.createParticlePacket(
            type, pos.x + 0.5, pos.y, pos.z + 0.5, 0f, 0f, 0f, count
        )

    fun createParticlePacketWithWriter(
        pos: Pos, type: Particle, count: Int, consumer: Consumer<BinaryWriter>
    ): ParticlePacket = ParticleCreator.createParticlePacket(
        type, true, pos.x + 0.5, pos.y, pos.z + 0.5, 0f, 0f, 0f, /* particleData = */ 0f, count, consumer
    )

    fun createParticleWithBlockState(pos: Pos, type: Particle, block: Block, count: Int): ParticlePacket =
        createParticlePacketWithWriter(pos, type, count) {
            it.writeVarInt(block.stateId().toInt())
        }

    fun createBlockParticle(pos: Pos, block: Block, count: Int): ParticlePacket =
        createParticleWithBlockState(pos, Particle.BLOCK, block, count)
}