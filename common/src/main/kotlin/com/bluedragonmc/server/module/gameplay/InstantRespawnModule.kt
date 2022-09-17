package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerRespawnEvent

/**
 * This module automatically respawns players when they die.
 * No configuration is necessary.
 */
class InstantRespawnModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            if (event.entity is Player && event.damage >= (event.entity.health + (event.entity as Player).additionalHearts)) {
                event.damage = 0.0f

                (event.entity as CustomPlayer).apply {
                    refreshHealth()

                    MinecraftServer.getGlobalEventHandler().call(
                        PlayerDeathEvent(
                            this,
                            lastDamageSource?.buildDeathScreenText(this),
                            lastDamageSource?.buildDeathMessage(this),
                        )
                    )

                    isDead = true
                    setFireForDuration(0)
                    isOnFire = false
                    pose = Entity.Pose.STANDING
                    velocity = Vec.ZERO

                    val respawnEvent = PlayerRespawnEvent(this)
                    EventDispatcher.call(respawnEvent)
                    teleport(respawnEvent.respawnPosition).thenRun { refreshAfterTeleport() }

                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                        isDead = false
                    }
                }
            }
        }.priority = -1
    }
}