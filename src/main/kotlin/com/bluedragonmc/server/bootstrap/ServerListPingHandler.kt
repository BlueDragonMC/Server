package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_3
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.FAVICON
import com.bluedragonmc.server.SERVER_NAME_GRADIENT
import com.bluedragonmc.server.utils.noBold
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.server.ServerListPingEvent
import net.minestom.server.ping.ServerListPingType
import net.minestom.server.ping.Status

object ServerListPingHandler : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(ServerListPingEvent::class.java) { event ->
            val versionString = Environment.versionInfo.run { "$BRANCH/$COMMIT" }
            val title = if (event.pingType != ServerListPingType.MODERN_FULL_RGB)
                Component.text("BlueDragon").withColor(BRAND_COLOR_PRIMARY_3)
            else SERVER_NAME_GRADIENT
            event.status = Status.builder().description(
                title.decorate(TextDecoration.BOLD) +
                        Component.space() +
                        Component.text("[", NamedTextColor.DARK_GRAY).noBold() +
                        Component.text(versionString, NamedTextColor.GREEN).noBold() +
                        Component.text("]", NamedTextColor.DARK_GRAY).noBold()
            )
                .favicon(FAVICON.toByteArray())
                .build()
        }
    }
}