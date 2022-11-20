package com.bluedragonmc.server

import com.bluedragonmc.server.module.database.PlayerDocument
import com.bluedragonmc.server.module.database.PunishmentType
import com.bluedragonmc.server.module.gameplay.ShopModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.PlayerMeta
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.potion.PotionEffect
import net.minestom.server.utils.async.AsyncUtils
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.log
import kotlin.math.pow

class CustomPlayer(uuid: UUID, username: String, playerConnection: PlayerConnection) :
    Player(uuid, username, playerConnection) {

    internal var isSpectating = false
    internal var lastNPCInteractionTime = 0L

    /**
     * Updated to the player's invisibility state when they go into spectator mode.
     * Used to determine whether the player should be invisible or not when they leave spectator mode.
     */
    private var wasInvisible = false

    var virtualItems = mutableListOf<ShopModule.VirtualItem>()

    lateinit var data: PlayerDocument

    fun isDataInitialized() = ::data.isInitialized

    fun getFirstMute() = if(isDataInitialized()) data.punishments.firstOrNull { it.type == PunishmentType.MUTE && it.isInEffect() } else null

    fun getFirstBan() = if(isDataInitialized()) data.punishments.firstOrNull { it.type == PunishmentType.BAN && it.isInEffect() } else null

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
        if(prevGameMode != GameMode.SPECTATOR && gameMode == GameMode.SPECTATOR) { // Entering spectator mode
            wasInvisible = isInvisible
            isInvisible = true // Make the player invisible so their floating head does not appear for everyone
        }
        if(prevGameMode == GameMode.SPECTATOR && gameMode != GameMode.SPECTATOR) { // Leaving spectator mode
            isInvisible = wasInvisible
        }
    }

    public override fun refreshHealth() { // Overridden to increase visibility
        super.refreshHealth()
    }

    public override fun refreshAfterTeleport() { // Overridden to increase visibility
        super.refreshAfterTeleport()
    }

    override fun setInstance(instance: Instance): CompletableFuture<Void> {
        try {
            Game.findGame(this)?.apply {
                val spawnpoint =
                    getModuleOrNull<SpawnpointModule>()?.spawnpointProvider?.getSpawnpoint(this@CustomPlayer)
                if (spawnpoint != null && instance != this@CustomPlayer.instance) {
                    return super.setInstance(instance, spawnpoint)
                }
            }
            return if (instance != this@CustomPlayer.instance) {
                super.setInstance(instance, if (this.instance != null) getPosition() else respawnPoint)
            } else {
                AsyncUtils.VOID_FUTURE
            }
        } catch (e: Throwable) {
            MinecraftServer.getExceptionManager().handleException(e)
            kick(Component.text("Failed to change instances! (${e.message})", NamedTextColor.RED))
            throw e
        }
    }

    override fun getAdditionalHearts(): Float {
        return if (entityMeta !is PlayerMeta) 0f
        else super.getAdditionalHearts()
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
    fun setDead(dead: Boolean) = refreshIsDead(dead)

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