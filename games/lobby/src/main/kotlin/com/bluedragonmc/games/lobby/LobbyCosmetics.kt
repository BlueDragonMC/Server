package com.bluedragonmc.games.lobby

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.CosmeticsModule
import com.bluedragonmc.server.module.gameplay.DoubleJumpModule
import com.bluedragonmc.server.utils.packet.PacketUtils
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.particle.Particle
import kotlin.math.cos
import kotlin.math.sin

@DependsOn(CosmeticsModule::class)
class LobbyCosmeticsModule : GameModule() {

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        val cosmetics = parent.getModule<CosmeticsModule>()

        eventNode.addListener(DoubleJumpModule.PlayerDoubleJumpEvent::class.java) { event ->
            when (cosmetics.getCosmeticInGroup<DoubleJumpEffect>(event.player)) {
                DoubleJumpEffect.DOUBLE_JUMP_NOTE -> {
                    (0 until 360 step 36).forEach { degrees ->
                        val radians = Math.toRadians(degrees.toDouble())
                        val packet = PacketUtils.createParticlePacket(
                            event.player.position.add(
                                cos(radians) * 2.5, 0.0, sin(radians) * 2.5
                            ), Particle.NOTE, 2
                        )
                        event.player.sendPacketToViewersAndSelf(packet)
                    }
                }
                null -> {}
            }
        }
    }

    enum class DoubleJumpEffect(override val id: String) : CosmeticsModule.Cosmetic {
        DOUBLE_JUMP_NOTE("lobby_double_jump_note")
    }
}