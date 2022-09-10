package com.bluedragonmc.server.bootstrap.dev

import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.queue.DevelopmentEnvironment
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object OpenToLAN : Bootstrap(DevelopmentEnvironment::class) {
    override fun hook(eventNode: EventNode<Event>) {
        net.minestom.server.extras.lan.OpenToLAN.open()
    }

}