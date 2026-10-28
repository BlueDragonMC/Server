package com.bluedragonmc.server.service

import com.bluedragonmc.server.api.IncomingRPCHandler
import com.bluedragonmc.server.api.OutgoingRPCHandler
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

object Messaging {

    lateinit var outgoing: OutgoingRPCHandler
        private set

    lateinit var incoming: IncomingRPCHandler
        private set

    private val ioJob = SupervisorJob()

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
            Dispatchers.IO + ioJob + CoroutineName("Messaging IO")
    }

    /**
     * Cancels pending messaging work and waits for it to finish.
     */
    fun shutdownIO() {
        ioJob.cancel()
        runBlocking {
            withTimeoutOrNull(5.seconds) {
                ioJob.join()
            }
        }
    }

    /**
     * Closes the outgoing gRPC channel.
     */
    fun closeOutgoing() {
        if (Messaging::outgoing.isInitialized) {
            outgoing.close()
        }
    }
}
