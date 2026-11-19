package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.IncomingRPCHandlerStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.impl.*
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Maps
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.service.Permissions
import com.bluedragonmc.server.utils.RpcErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds

object IntegrationsInit : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        Database.initialize(DatabaseConnectionImpl(Environment.mongoConnectionString))
        Permissions.initialize(PermissionManagerImpl())
        Maps.registerMapProvider(CommonTypes.MapFormat.POLAR, PolarMapProvider())
        Maps.registerMapProvider(CommonTypes.MapFormat.ANVIL, AnvilMapProvider())

        if (Environment.current.isDev) {
            logger.info("Using no-op stubs for messaging as this server is in a development environment.")
            Messaging.initializeIncoming(IncomingRPCHandlerStub())
            Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
        } else {
            logger.info("Attempting to connect to messaging at address ${InetAddress.getByName(Environment.puffinHostname).hostAddress}")
            Messaging.initializeIncoming(IncomingRPCHandlerImpl(Environment.grpcServerPort))
            Messaging.initializeOutgoing(OutgoingRPCHandlerImpl(Environment.puffinHostname, Environment.puffinPort))
            runBlocking {
                registerWithPuffin()
            }
        }
    }

    /** How many times this server will try to register with Puffin on startup before giving up. */
    private const val REGISTRATION_ATTEMPTS = 10

    /** Delay between Puffin registration attempts. */
    private val REGISTRATION_RETRY_DELAY_MS = 2_000L.milliseconds

    private suspend fun registerWithPuffin() {
        val serverName = Environment.getServerName()
        for (attempts in 1..REGISTRATION_ATTEMPTS) {
            try {
                Messaging.outgoing.initGameServer(serverName)
                if (attempts > 1) {
                    logger.info("Registered with Puffin after ${attempts + 1} attempts.")
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempts >= REGISTRATION_ATTEMPTS) {
                    RpcErrors.log("Failed to register with Puffin after $attempts attempts", e, logger)
                    throw e
                }
                RpcErrors.log(
                    "Failed to register with Puffin (attempt $attempts of $REGISTRATION_ATTEMPTS); " +
                            "retrying in ${REGISTRATION_RETRY_DELAY_MS}ms",
                    e,
                    logger
                )
                delay(REGISTRATION_RETRY_DELAY_MS)
            }
        }
    }
}
