package com.bluedragonmc.server

import com.bluedragonmc.server.module.database.PlayerDocument
import com.bluedragonmc.server.module.gameplay.ShopModule
import net.minestom.server.entity.Entity
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.item.Material
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.potion.PotionEffect
import java.util.*

class CustomPlayer(uuid: UUID, username: String, playerConnection: PlayerConnection) :
    Player(uuid, username, playerConnection) {

    internal var isSpectating = false
    internal var lastNPCInteractionTime = 0L

    internal var virtualItems = mutableListOf<ShopModule.VirtualItem>()

    internal lateinit var data: PlayerDocument

    fun isDataInitialized() = ::data.isInitialized

    override fun spectate(entity: Entity) {
        super.spectate(entity)
        isSpectating = true
    }

    override fun stopSpectating() {
        super.stopSpectating()
        isSpectating = false
    }

    override fun setSneaking(sneaking: Boolean) {
        super.setSneaking(sneaking)
        if (sneaking && isSpectating && gameMode == GameMode.SPECTATOR) {
            stopSpectating()
        }
    }

    override fun setGameMode(gameMode: GameMode) {
        val prevGameMode = this.gameMode
        super.setGameMode(gameMode)
        // When a player stops spectating, send a camera packet to make sure they are not stuck.
        if (isSpectating && prevGameMode == GameMode.SPECTATOR && gameMode != GameMode.SPECTATOR) {
            stopSpectating()
        }
    }

    fun isOnLadder() = instance!!.getBlock(position).registry().material() == Material.LADDER

    fun isInWater() = instance!!.getBlock(position).isLiquid

    fun isBlind() = activeEffects.any { it.potion.effect == PotionEffect.BLINDNESS }

    companion object {
        /**
         * Gets the XP level based on the total number of XP specified.
         */
        fun getXpLevel(experience: Int): Int {
            return (experience / 5000F).toInt()
        }

        /**
         * Given the total number of XP the player has, gets their progress to levelling up.
         * At 0% (0), they have just levelled up. At 100% (1), the player will level up.
         */
        fun getXpPercent(experience: Int): Float {
            return experience % 5000F / 5000F
        }
    }

}