package com.bluedragonmc.server

import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.item.Material
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.potion.PotionEffect
import java.util.*

class CustomPlayer(uuid: UUID, username: String, playerConnection: PlayerConnection) :
    Player(uuid, username, playerConnection) {

    var fallDistance = 0.0

    override fun tick(time: Long) {
        super.tick(time)
        if (hurtResistantTime > 0) hurtResistantTime--
    }

    fun isOnLadder() = instance!!.getBlock(position).registry().material() == Material.LADDER

    fun isInWater() = instance!!.getBlock(position).isLiquid

    fun isBlind() = activeEffects.any { it.potion.effect == PotionEffect.BLINDNESS }

}