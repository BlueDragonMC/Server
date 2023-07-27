package com.bluedragonmc.server.bootstrap

import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerDeathEvent

object PerInstanceChat : Bootstrap() {

    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerChatEvent::class.java) { event ->
            // Restrict player chat messages to only be visible in the player's current instance.
            event.recipients.removeAll { it.instance != event.player.instance }
        }

        val child = EventNode.event("per-instance-chat", EventFilter.ALL) { true }
        child.priority = Integer.MAX_VALUE // High priority; runs last
        eventNode.addChild(child)

        child.addListener(PlayerDeathEvent::class.java) { event ->
            // Cancel the regular death message and only send it to the dead player's instance.
            val message = event.chatMessage
            event.chatMessage = null
            if (message != null) {
                event.instance.sendMessage(message)
            }
        }
    }
}