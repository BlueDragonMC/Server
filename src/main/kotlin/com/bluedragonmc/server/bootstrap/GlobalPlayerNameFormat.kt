package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.event.DataLoadedEvent
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object GlobalPlayerNameFormat : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(DataLoadedEvent::class.java) { event ->
            (event.player as CustomPlayer).updateDisplayName(null)
        }
    }
}