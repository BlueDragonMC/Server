package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.messages.ReportErrorMessage
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.queue.ProductionEnvironment
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object CustomExceptionHandler : Bootstrap(ProductionEnvironment::class) {
    override fun hook(eventNode: EventNode<Event>) {
        // Automatically report errors to Puffin in addition to logging them
        MinecraftServer.getExceptionManager().setExceptionHandler { e ->
            e.printStackTrace()
            DatabaseModule.IO.launch {
                MessagingModule.publish(ReportErrorMessage(MessagingModule.containerId,
                    null,
                    e.message.orEmpty(),
                    e.stackTraceToString(),
                    getDebugContext()))
            }
        }
    }

    private fun getDebugContext() = mapOf(
        "Container ID" to MessagingModule.containerId.toString(),
        "All Running Instances" to MinecraftServer.getInstanceManager().instances.joinToString { it.uniqueId.toString() },
        "Running Games" to Game.games.joinToString { it.toString() },
        "Online Players" to MinecraftServer.getConnectionManager().onlinePlayers.joinToString { it.uuid.toString() }
    )
}