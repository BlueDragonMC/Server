package com.bluedragonmc.server.bootstrap.dev

import com.bluedragonmc.server.bootstrap.Bootstrap
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object OpenToLAN : Bootstrap(EnvType.DEVELOPMENT) {
    override fun hook(eventNode: EventNode<Event>) {
        net.minestom.server.extras.lan.OpenToLAN.open()
    }

}