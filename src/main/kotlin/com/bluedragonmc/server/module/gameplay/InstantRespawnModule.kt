package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDeathEvent
import java.time.Duration

/**
 * This module automatically respawns players when they die.
 * No configuration is necessary.
 */
class InstantRespawnModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            MinecraftServer.getSchedulerManager().buildTask {
                event.player.respawn()
            }.delay(Duration.ofMillis(15)).schedule()
        }
    }
}