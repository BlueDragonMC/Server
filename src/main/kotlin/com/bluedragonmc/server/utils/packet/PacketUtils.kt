package com.bluedragonmc.server.utils.packet

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket

object PacketUtils {
    fun getRelativePosLookPacket(player: Player, position: Pos) = PlayerPositionAndLookPacket(
        position.withView(0.0f, 0.0f),
        (0x08 or 0x10).toByte(), // flags - see https://wiki.vg/Protocol#Synchronize_Player_Position
        player.nextTeleportId,
        false
    )
}