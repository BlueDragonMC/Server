package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.SERVER_NAME_GRADIENT
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent

object TabListFormat : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.sendPlayerListHeaderAndFooter(SERVER_NAME_GRADIENT.decorate(TextDecoration.BOLD),
                Component.translatable("global.tab.call_to_action",
                    BRAND_COLOR_PRIMARY_2,
                    Component.translatable("global.server.domain", BRAND_COLOR_PRIMARY_1)))
        }
    }
}