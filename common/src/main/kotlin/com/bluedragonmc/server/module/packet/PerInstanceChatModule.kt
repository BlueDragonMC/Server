package com.bluedragonmc.server.module.packet

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerDeathEvent

class PerInstanceChatModule : GameModule() {

    override val eventPriority = 99 // higher = runs last

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerChatEvent::class.java) { event ->
            // Restrict player chat messages to only be visible in the player's current instance.
            event.recipients.removeAll { it.instance != event.player.instance }
        }
        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            // Cancel the regular death message and only send it to the dead player's instance.
            val message = event.chatMessage
            event.chatMessage = null
            if (message != null) {
                event.instance.sendMessage(message)
            }
        }
    }
}