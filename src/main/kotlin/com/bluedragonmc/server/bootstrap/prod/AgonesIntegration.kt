package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.queue.ProductionEnvironment
import dev.cubxity.libs.agones.AgonesSDK
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.time.Duration

object AgonesIntegration : Bootstrap(ProductionEnvironment::class) {

    val sdk = AgonesSDK()

    // The amount of milliseconds in between health check pings
    private const val HEALTH_CHECK_INTERVAL = 1_000L

    // When players are online, keep the server reserved for twice the healthcheck interval.
    private const val RESERVATION_TIME = HEALTH_CHECK_INTERVAL * 2L

    override fun hook(eventNode: EventNode<Event>) {

        val healthFlow = flow {
            while(true) {
                // Send a health message every second
                emit(Unit)
                delay(HEALTH_CHECK_INTERVAL)
                if (MinecraftServer.getConnectionManager().onlinePlayers.isNotEmpty()) {
                    sdk.reserve(Duration.ofMillis(RESERVATION_TIME))
                }
            }
        }

        DatabaseModule.IO.launch {
            logger.info("Agones - Starting health check pings")
            sdk.health(healthFlow)
        }

        runBlocking {
            sdk.ready()
            logger.info("Agones - Ready")
        }
    }
}