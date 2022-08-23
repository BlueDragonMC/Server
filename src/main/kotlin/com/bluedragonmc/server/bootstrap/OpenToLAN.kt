package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.Environment
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object OpenToLAN : Bootstrap(Environment.DevelopmentEnvironment::class) {
    override fun hook(eventNode: EventNode<Event>) {
        net.minestom.server.extras.lan.OpenToLAN.open()
    }

}