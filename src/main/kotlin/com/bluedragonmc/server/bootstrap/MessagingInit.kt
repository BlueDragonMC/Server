package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.IncomingRPCHandlerStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.impl.IncomingRPCHandlerImpl
import com.bluedragonmc.server.impl.OutgoingRPCHandlerImpl
import com.bluedragonmc.server.queue.ProductionEnvironment
import com.bluedragonmc.server.service.Messaging
import kotlinx.coroutines.runBlocking
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.net.InetAddress

object MessagingInit : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        if (Environment.current is ProductionEnvironment) {
            initProd()
        } else {
            initDev()
        }
    }

    private fun initProd() {
        logger.info("Attempting to connect to messaging at address ${InetAddress.getByName(Environment.puffinHostname).hostAddress}")
        Messaging.initializeIncoming(IncomingRPCHandlerImpl())
        Messaging.initializeOutgoing(OutgoingRPCHandlerImpl(Environment.puffinHostname))
        runBlocking {
            Messaging.outgoing.initGameServer(Environment.getServerName())
        }
    }

    private fun initDev() {
        logger.info("Using no-op stubs for messaging as this server is in a development environment.")
        Messaging.initializeIncoming(IncomingRPCHandlerStub())
        Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
    }
}
