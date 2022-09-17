package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent

/**
 * A module that respawns the player when their height goes below a certain threshold.
 * @param threshold Minimum height the player can be at without being respawned.
 * @param respawnMode True if the player should just be respawned instead of being killed.
 */
class VoidDeathModule(private val threshold: Double, private val respawnMode: Boolean = false) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (event.player.position.y < threshold && !event.player.isDead) {
                if (respawnMode) {
                    event.player.respawn()
                    event.player.teleport(event.player.respawnPoint)
                } else {
                    event.player.damage(DamageType.VOID, Float.MAX_VALUE)
                }
            }
        }
    }
}