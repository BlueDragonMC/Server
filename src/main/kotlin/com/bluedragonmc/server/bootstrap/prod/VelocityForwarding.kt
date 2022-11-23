package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.queue.ProductionEnvironment
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.extras.velocity.VelocityProxy

object VelocityForwarding : Bootstrap(ProductionEnvironment::class) {
    override fun hook(eventNode: EventNode<Event>) {
        VelocityProxy.enable(System.getenv("PUFFIN_VELOCITY_SECRET").trim())
    }
}