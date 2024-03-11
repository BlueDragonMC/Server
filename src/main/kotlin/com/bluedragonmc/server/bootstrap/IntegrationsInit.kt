package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.IncomingRPCHandlerStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.impl.DatabaseConnectionImpl
import com.bluedragonmc.server.impl.IncomingRPCHandlerImpl
import com.bluedragonmc.server.impl.OutgoingRPCHandlerImpl
import com.bluedragonmc.server.impl.PermissionManagerImpl
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.service.Permissions
import kotlinx.coroutines.runBlocking
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.net.InetAddress

object IntegrationsInit : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        Database.initialize(DatabaseConnectionImpl(Environment.mongoConnectionString))
        Permissions.initialize(PermissionManagerImpl())

        if (Environment.current.isDev) {
            logger.info("Using no-op stubs for messaging as this server is in a development environment.")
            Messaging.initializeIncoming(IncomingRPCHandlerStub())
            Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
        } else {
            logger.info("Attempting to connect to messaging at address ${InetAddress.getByName(Environment.puffinHostname).hostAddress}")
            Messaging.initializeIncoming(IncomingRPCHandlerImpl(Environment.grpcServerPort))
            Messaging.initializeOutgoing(OutgoingRPCHandlerImpl(Environment.puffinHostname, Environment.puffinPort))
            runBlocking {
                Messaging.outgoing.initGameServer(Environment.getServerName())
            }
        }
    }
}
