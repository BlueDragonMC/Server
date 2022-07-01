package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.attribute.Attribute
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import java.time.Duration

/**
 * "Resets" the player when the game starts. This changes some basic attributes to make sure effects don't persist in between games.
 * - Change game mode
 * - Clear inventory
 * - Reset health/hunger
 * - Reset movement speed
 * - Clear potion effects
 * - Disable flying
 * - Stop fire damage
 * - Disable glowing
 * - Reset XP
 */
class PlayerResetModule(val defaultGameMode: GameMode? = null) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            resetPlayer(event.player, defaultGameMode)
        }
    }

    fun resetPlayer(player: Player, gameMode: GameMode? = defaultGameMode) {
        player.gameMode = gameMode ?: player.gameMode
        player.inventory.clear()
        player.getAttribute(Attribute.MAX_HEALTH).modifiers.clear()
        player.getAttribute(Attribute.MOVEMENT_SPEED).modifiers.clear()
        player.health = player.maxHealth
        player.food = 20
        player.clearEffects()
        player.setFireDamagePeriod(Duration.ZERO)
        player.isGlowing = false
        player.isAllowFlying = false
        player.level = 0
        player.exp = 0F
        player.stopSpectating()
    }
}