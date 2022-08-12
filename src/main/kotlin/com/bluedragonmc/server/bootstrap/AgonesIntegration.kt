package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.Environment
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

    override fun hook(eventNode: EventNode<Event>) {

        var lastReservedTime = 0L

        DatabaseModule.IO.launch {
            sdk.health(flow {
                while (true) {
                    // Send a health message every second
                    emit(Unit)
                    delay(1_000)
                }
            })
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