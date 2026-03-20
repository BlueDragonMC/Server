package com.bluedragonmc.server.bootstrap.dev

import com.bluedragonmc.server.bootstrap.Bootstrap
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object MojangAuthentication : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        if (System.getenv("PUFFIN_VELOCITY_SECRET") == null) {
            MinecraftServer.updateProcess(Auth.Online())
        }
    }
}