package com.bluedragonmc.server.bootstrap.dev

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.isLobbyInitialized
import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.queue.DevelopmentEnvironment
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket

object DevInstanceRouter : Bootstrap(DevelopmentEnvironment::class) {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(AsyncPlayerPreLoginEvent::class.java) { event ->
            DatabaseModule.loadDataDocument(event.player as CustomPlayer)

            if (!isLobbyInitialized()) {
                event.player.sendPacket(LoginDisconnectPacket(Component.text("Lobby not initialized yet! Please wait a few seconds...", NamedTextColor.RED)))
                event.player.playerConnection.disconnect()
            }
        }
        eventNode.addListener(PlayerLoginEvent::class.java) { event ->
            event.setSpawningInstance(lobby.getInstance())
            lobby.players.add(event.player)
        }
    }
}