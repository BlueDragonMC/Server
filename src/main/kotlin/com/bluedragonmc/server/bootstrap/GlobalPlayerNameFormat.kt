package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object GlobalPlayerNameFormat : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(DataLoadedEvent::class.java) { event ->
            val player = event.player as CustomPlayer
            val group = player.data.highestGroup
            if (group == null) {
                player.displayName = player.username withColor NamedTextColor.GRAY
                return@addListener
            }
            player.displayName = player.username withColor group.color
        }
    }
}