package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.impl.DatabaseConnectionImpl
import com.bluedragonmc.server.service.Database
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object DatabaseInit : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        Database.initialize(DatabaseConnectionImpl("mongodb://${Environment.mongoHostname}"))
    }
}
