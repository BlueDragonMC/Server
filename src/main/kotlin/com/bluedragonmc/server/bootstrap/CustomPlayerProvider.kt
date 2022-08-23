package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.CustomPlayer
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object CustomPlayerProvider : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        // Set a custom player provider, so we can easily add fields to the Player class
        MinecraftServer.getConnectionManager().setPlayerProvider(::CustomPlayer)
    }
}