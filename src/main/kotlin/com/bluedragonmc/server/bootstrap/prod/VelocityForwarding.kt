package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.bootstrap.Bootstrap
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object VelocityForwarding : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        if (System.getenv("PUFFIN_VELOCITY_SECRET") != null) {
            MinecraftServer.updateProcess(Auth.Velocity(System.getenv("PUFFIN_VELOCITY_SECRET").trim()))
            MinecraftServer.setCompressionThreshold(0) // Disable compression because packets are being proxied
        } else if (!Environment.current.isDev) {
            logger.warn("Warning: Running in a production-like environment without Velocity forwarding!")
        }
    }
}