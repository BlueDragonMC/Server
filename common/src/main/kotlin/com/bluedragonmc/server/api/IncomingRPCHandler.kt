package com.bluedragonmc.server.api

/**
 * Represents a messaging handler that receives messages
 * from other services and performs actions based on them.
 */
interface IncomingRPCHandler {

    fun isConnected(): Boolean

    fun shutdown()


}