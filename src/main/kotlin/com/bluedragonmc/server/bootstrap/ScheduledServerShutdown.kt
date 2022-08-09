package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.Environment
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.time.Duration
import kotlin.system.exitProcess

object ScheduledServerShutdown : Bootstrap(Environment.ProductionEnvironment::class) {
    override fun hook(eventNode: EventNode<Event>) {
        // Make the server shutdown after 6 hours (if there are no players online)
        MinecraftServer.getSchedulerManager().buildTask {
            if (MinecraftServer.getConnectionManager().onlinePlayers.isEmpty()) {
                MinecraftServer.stopCleanly()
                exitProcess(0)
            }
        }.delay(Duration.ofHours(6)).repeat(Duration.ofSeconds(60)).schedule()
    }
}