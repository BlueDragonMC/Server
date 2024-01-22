package com.bluedragonmc.server.bootstrap.dev

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.isLobbyInitialized
import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.utils.listen
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent

object DevInstanceRouter : Bootstrap(EnvType.DEVELOPMENT) {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(AsyncPlayerPreLoginEvent::class.java) { event ->
            Database.connection.loadDataDocument(event.player as CustomPlayer)
        }
        eventNode.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            if (isLobbyInitialized()) {
                // Send the player to the lobby
                event.spawningInstance = lobby.getInstance()
                lobby.players.add(event.player)
            } else {
                // Send the player to a temporary "limbo" instance while the lobby is being loaded
                val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
                instance.enableAutoChunkLoad(false)
                instance.eventNode().listen<InstanceTickEvent> {
                    if (instance.players.isEmpty()) {
                        MinecraftServer.getInstanceManager().unregisterInstance(instance)
                    }
                    if (isLobbyInitialized()) {
                        lobby.addPlayer(event.player)
                    }
                }
                event.spawningInstance = instance
            }
        }
    }
}