package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.Environment
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.extras.MojangAuth

object MojangAuthentication : Bootstrap(Environment.DevelopmentEnvironment::class) {
    override fun hook(eventNode: EventNode<Event>) {
        MojangAuth.init()
    }
}