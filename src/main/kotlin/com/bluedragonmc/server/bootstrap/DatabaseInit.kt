package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.Database
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object DatabaseInit : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        Database.initialize("mongodb://${Environment.current.mongoHostname}")
    }
}
