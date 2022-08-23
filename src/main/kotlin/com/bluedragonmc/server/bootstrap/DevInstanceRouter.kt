package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.lobby
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerLoginEvent

object DevInstanceRouter : Bootstrap(Environment.DevelopmentEnvironment::class) {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerLoginEvent::class.java) { event ->
            event.setSpawningInstance(lobby.getInstance())
        }
    }
}