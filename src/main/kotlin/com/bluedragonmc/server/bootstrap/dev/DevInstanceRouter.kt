package com.bluedragonmc.server.bootstrap.dev

import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.isLobbyInitialized
import com.bluedragonmc.server.lobby
import net.kyori.adventure.text.Component
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerLoginEvent

object DevInstanceRouter : Bootstrap(Environment.DevelopmentEnvironment::class) {
    override fun hook(eventNode: EventNode<Event>) {
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