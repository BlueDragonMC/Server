package com.bluedragonmc.server

import net.minestom.server.entity.Entity
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.potion.PotionEffect
import java.util.*

class CustomPlayer(uuid: UUID, username: String, playerConnection: PlayerConnection) :
    Player(uuid, username, playerConnection) {

    private var spectating = false

    override fun spectate(entity: Entity) {
        super.spectate(entity)
        spectating = true
    }

    override fun stopSpectating() {
        super.stopSpectating()
        spectating = false
    }

    override fun setSneaking(sneaking: Boolean) {
        super.setSneaking(sneaking)
        if (sneaking && spectating && gameMode == GameMode.SPECTATOR) {
            stopSpectating()
        }
    }

    override fun setGameMode(gameMode: GameMode) {
        val prevGameMode = this.gameMode
        super.setGameMode(gameMode)
        // When a player stops spectating, send a camera packet to make sure they are not stuck.
        if (spectating && prevGameMode == GameMode.SPECTATOR && gameMode != GameMode.SPECTATOR) {
            stopSpectating()
        }
    }

    fun isOnLadder() = instance!!.getBlock(position).registry().material() == Material.LADDER

    fun isInWater() = instance!!.getBlock(position).isLiquid

    fun isBlind() = activeEffects.any { it.potion.effect == PotionEffect.BLINDNESS }

}