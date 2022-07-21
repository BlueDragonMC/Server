package com.bluedragonmc.server.utils.packet

import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerDeathEvent

object PerInstanceChat {
    fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerChatEvent::class.java) { event ->
            // Restrict player chat messages to only be visible in the player's current instance.
            event.recipients.removeAll { it.instance != event.player.instance }
        }
        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            // Cancel the regular death message and only send it to the dead player's instance.
            val message = event.chatMessage
            if (message != null) {
                event.instance.sendMessage(message)
                event.chatMessage = null
            }
        }.apply {
            // Trigger after all other listeners have modified the message
            priority = -1
        }
    }
}