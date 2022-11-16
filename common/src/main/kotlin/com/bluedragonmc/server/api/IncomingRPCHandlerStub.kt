package com.bluedragonmc.server.api

/**
 * Stub - no functionality. Used in development and testing environments.
 * See [com.bluedragonmc.server.impl.IncomingRPCHandlerImpl]
 * for a full implementation.
 */
class IncomingRPCHandlerStub : IncomingRPCHandler {
    override fun isConnected(): Boolean {
        return true
    }

    override fun shutdown() {

    }

}
