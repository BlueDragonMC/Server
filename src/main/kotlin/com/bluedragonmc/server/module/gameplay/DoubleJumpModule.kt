package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerPacketOutEvent
import net.minestom.server.event.player.PlayerStartFlyingEvent
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket
import kotlin.math.cos
import kotlin.math.sin

object DoubleJumpModule : GameModule() {

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerPacketOutEvent::class.java) { event ->
            if (event.packet is ChangeGameStatePacket) {
                event.player.isAllowFlying = true
            }
        }
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (event.isOnGround) {
                if(!event.player.isAllowFlying) event.player.isAllowFlying = true
            }
        }
        eventNode.addListener(PlayerStartFlyingEvent::class.java) { event ->
            if (event.player.gameMode == GameMode.CREATIVE || event.player.gameMode == GameMode.SPECTATOR) return@addListener
            event.player.isFlying = false
            event.player.isAllowFlying = false
            val strength = 25.0
            val x = -sin(Math.toRadians(event.player.position.yaw.toDouble())) * strength
            val z = cos(Math.toRadians(event.player.position.yaw.toDouble())) * strength
            event.player.velocity = event.player.velocity.add(
                x, 15.0, z
            )
        }
    }
}