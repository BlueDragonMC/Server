package com.bluedragonmc.server.service

import com.bluedragonmc.server.api.IncomingRPCHandler
import com.bluedragonmc.server.api.OutgoingRPCHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

object Messaging {

    lateinit var outgoing: OutgoingRPCHandler
        private set

    lateinit var incoming: IncomingRPCHandler
        private set

    fun initializeOutgoing(connection: OutgoingRPCHandler) {
        outgoing = connection
    }

    fun initializeIncoming(handler: IncomingRPCHandler) {
        incoming = handler
    }

    fun isConnected(): Boolean =
        Messaging::incoming.isInitialized && incoming.isConnected() && Messaging::outgoing.isInitialized && outgoing.isConnected()

    val IO = object : CoroutineScope {
        override val coroutineContext: CoroutineContext =
            Dispatchers.IO + SupervisorJob() + CoroutineName("Messaging IO")
    }
}