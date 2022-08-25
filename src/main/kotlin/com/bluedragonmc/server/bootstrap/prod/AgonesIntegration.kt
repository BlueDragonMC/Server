package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.module.database.DatabaseModule
import dev.cubxity.libs.agones.AgonesSDK
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.time.Duration

object AgonesIntegration : Bootstrap(Environment.ProductionEnvironment::class) {

    val sdk = AgonesSDK()

    // Reserve the server every 10 seconds while players are online
    private const val RESERVATION_INTERVAL = 10_000L

    // Every time the server is reserved, keep it reserved for 20 seconds.
    private const val RESERVATION_TIME = 20_000L

    // The amount of milliseconds in between health check pings
    private const val HEALTH_CHECK_INTERVAL = 1_000L

    override fun hook(eventNode: EventNode<Event>) {

        var lastReservedTime = 0L

        val healthFlow = flow {
            while(true) {
                // Send a health message every second
                emit(Unit)
                delay(HEALTH_CHECK_INTERVAL)
            }
        }

        DatabaseModule.IO.launch {
            logger.info("Agones - Starting health check pings")
            sdk.health(healthFlow)
        }

        DatabaseModule.IO.launch {
            sdk.ready()
            logger.info("Agones - Ready")

            while (true) {
                if (MinecraftServer.getConnectionManager().onlinePlayers.isNotEmpty() && System.currentTimeMillis() - lastReservedTime > RESERVATION_INTERVAL) {
                    lastReservedTime = System.currentTimeMillis()
                    sdk.reserve(Duration.ofMillis(RESERVATION_TIME))
                }
                delay(2_000)
            }
        }
    }
}