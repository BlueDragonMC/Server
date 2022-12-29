package com.bluedragonmc.server.bootstrap.prod

import agones.dev.sdk.SDKGrpcKt
import agones.dev.sdk.duration
import agones.dev.sdk.empty
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.util.concurrent.TimeUnit

object AgonesIntegration : Bootstrap(EnvType.PRODUCTION) {

    private lateinit var channel: ManagedChannel
    lateinit var stub: SDKGrpcKt.SDKCoroutineStub
    val empty = empty { }

    // The amount of milliseconds in between health check pings
    private const val HEALTH_CHECK_INTERVAL = 10_000L

    // When players are online, keep the server reserved for twice the healthcheck interval.
    private const val RESERVATION_TIME = HEALTH_CHECK_INTERVAL * 2L

    override fun hook(eventNode: EventNode<Event>) {

        channel = ManagedChannelBuilder
            .forAddress("localhost", System.getProperty("AGONES_SDK_GRPC_PORT")?.toIntOrNull() ?: 9357)
            .usePlaintext()
            .build()
        stub = SDKGrpcKt.SDKCoroutineStub(channel)

        runBlocking {
            logger.info("Agones supplied server name: ${stub.getGameServer(empty).objectMeta.name}")
        }

        val healthFlow = flow {
            while(true) {
                // Send a health message every second
                if (isHealthy()) {
                    emit(empty)
                }
                delay(HEALTH_CHECK_INTERVAL)
                if (MinecraftServer.getConnectionManager().onlinePlayers.isNotEmpty()) {
                    stub.reserve(duration {
                        seconds = RESERVATION_TIME / 1000L
                    })
                }
            }
        }

        Database.IO.launch {
            logger.info("Agones - Starting health check pings")
            stub.health(healthFlow)
        }

        runBlocking {
            stub.ready(empty)
            logger.info("Agones - Ready")
        }

        MinecraftServer.getSchedulerManager().buildShutdownTask {
            channel.shutdown()
            channel.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun isHealthy(): Boolean {
        // Verify that at least one game is running (Lobby)
        if (Game.games.isEmpty()) return false
        // Verify that the local gRPC server is running
        if (!Messaging.isConnected()) return false

        return true
    }
}