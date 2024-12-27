package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent

/**
 * "Resets" the player when they join the game. This changes some basic attributes to make sure effects don't persist in between games.
 * - Change game mode
 * - Clear inventory
 * - Reset health/hunger
 * - Reset movement speed
 * - Clear potion effects
 * - Disable flying
 * - Stop fire damage
 * - Disable glowing
 * - Reset XP
 * - Clear all tags
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
        Attribute.values().forEach { attribute ->
            player.getAttribute(attribute).modifiers().forEach { modifier ->
                player.getAttribute(attribute).removeModifier(modifier)
            }
        }
        player.health = player.getAttribute(Attribute.MAX_HEALTH).value.toFloat()
        player.food = 20
        player.clearEffects()
        player.fireTicks = 0
        player.isGlowing = false
        player.isAllowFlying = false
        player.level = 0
        player.exp = 0F
        player.tagHandler().updateContent(CompoundBinaryTag.empty())
        player.stopSpectating()
    }
}