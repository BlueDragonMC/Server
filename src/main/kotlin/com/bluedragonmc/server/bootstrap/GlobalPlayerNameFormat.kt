package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.service.Permissions
import com.bluedragonmc.server.utils.withColor
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object GlobalPlayerNameFormat : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(DataLoadedEvent::class.java) { event ->
            event.player.displayName = event.player.name.withColor(
                Permissions.getMetadata(event.player.uuid).rankColor
            )
        }
    }
}