package com.bluedragonmc.server

import com.bluedragonmc.server.module.database.PlayerDocument
import com.bluedragonmc.server.module.database.PunishmentType
import com.bluedragonmc.server.module.gameplay.ShopModule
import net.minestom.server.entity.Entity
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.potion.PotionEffect
import java.util.*
import kotlin.math.log
import kotlin.math.pow

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

    fun isOnLadder() = listOf(
        Block.LADDER,
        Block.VINE,
        Block.CAVE_VINES,
        Block.TWISTING_VINES,
        Block.WEEPING_VINES
    ).any { instance!!.getBlock(position).compare(it) }

    fun isInWater() = instance!!.getBlock(position).isLiquid

    fun isBlind() = activeEffects.any { it.potion.effect == PotionEffect.BLINDNESS }

    companion object {
        /**
         * Gets the XP level based on the total number of XP specified.
         */
        fun getXpLevel(experience: Int): Double {
            return if (experience < 45000) (log(experience / 1000.0 + 1.0, 1.2) + 1)
            else experience / 10000.0 + 18.0
        }

        /**
         * Given the precise level of the player, gets their progress to levelling up.
         * At 0% (0), they have just levelled up. At 100% (1), the player will level up.
         */
        fun getXpPercent(currentLevel: Double): Float {
            return currentLevel.toFloat() % 1.0F
        }

        /**
         * Returns the amount of XP required to reach the next level.
         * @param currentLevel The player's current level.
         * @param totalExperience The total amount of XP the player currently has.
         */
        fun getXpToNextLevel(currentLevel: Double, totalExperience: Int): Int {
            return (getXpOfLevel((currentLevel + 1).toInt()) - totalExperience)
        }

        /**
         * Returns the total amount of XP needed to reach a certain level.
         */
        private fun getXpOfLevel(level: Int): Int {
            val xp = (1000 * 1.2.pow(level - 1.0) - 1000).toInt()
            if (xp < 45000) return xp
            return 10000 * level - 180000
        }
    }

}