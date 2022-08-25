package com.bluedragonmc.server.bootstrap.dev

import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.bootstrap.Bootstrap
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.extras.MojangAuth

object MojangAuthentication : Bootstrap(Environment.DevelopmentEnvironment::class) {
    override fun hook(eventNode: EventNode<Event>) {
        MojangAuth.init()
    }
}