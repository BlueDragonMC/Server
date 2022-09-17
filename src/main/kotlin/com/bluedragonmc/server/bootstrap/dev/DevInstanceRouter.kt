package com.bluedragonmc.server.bootstrap.dev

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.isLobbyInitialized
import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.queue.DevelopmentEnvironment
import net.kyori.adventure.text.Component
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent
import net.minestom.server.event.player.PlayerLoginEvent

object DevInstanceRouter : Bootstrap(DevelopmentEnvironment::class) {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(AsyncPlayerPreLoginEvent::class.java) { event ->
            DatabaseModule.loadDataDocument(event.player as CustomPlayer)
        }
        eventNode.addListener(PlayerLoginEvent::class.java) { event ->
            if (!isLobbyInitialized()) {
                event.player.kick(Component.text("Lobby not initialized yet!"))
            }
            else {
                event.setSpawningInstance(lobby.getInstance())
                lobby.players.add(event.player)
            }
        }
    }
}