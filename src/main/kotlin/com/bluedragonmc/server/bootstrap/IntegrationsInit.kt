package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.IncomingRPCHandlerStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.impl.DatabaseConnectionImpl
import com.bluedragonmc.server.impl.IncomingRPCHandlerImpl
import com.bluedragonmc.server.impl.OutgoingRPCHandlerImpl
import com.bluedragonmc.server.impl.PermissionManagerImpl
import com.bluedragonmc.server.queue.DevelopmentEnvironment
import com.bluedragonmc.server.queue.ProductionEnvironment
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.service.Permissions
import kotlinx.coroutines.runBlocking
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.net.InetAddress

object IntegrationsInit : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {

        Database.initialize(DatabaseConnectionImpl("mongodb://${Environment.mongoHostname}"))

        when (Environment.current) {
            is DevelopmentEnvironment -> {
                logger.info("Using no-op stubs for messaging as this server is in a development environment.")
                Messaging.initializeIncoming(IncomingRPCHandlerStub())
                Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
                Permissions.initialize(PermissionManagerImpl("http://localhost:8080"))
            }
            is ProductionEnvironment -> {
                logger.info("Attempting to connect to messaging at address ${InetAddress.getByName(Environment.puffinHostname).hostAddress}")
                Messaging.initializeIncoming(IncomingRPCHandlerImpl())
                Messaging.initializeOutgoing(OutgoingRPCHandlerImpl(Environment.puffinHostname))
                runBlocking {
                    Messaging.outgoing.initGameServer(Environment.getServerName())
                }
                Permissions.initialize(PermissionManagerImpl("http://luckperms:8080"))
            }
            else -> error("Unexpected environment type: ${Environment.current::class}")
        }
    }

}
