package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.*
import com.bluedragonmc.server.utils.buildComponent
import com.bluedragonmc.server.utils.center
import com.bluedragonmc.server.utils.withColor
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.withGradient
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.server.ServerListPingEvent
import net.minestom.server.ping.ServerListPingType
import java.net.InetAddress

object ServerListPingHandler : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(ServerListPingEvent::class.java) { event ->
            val title = buildComponent {
                val title = Component.text("BlueDragon", BRAND_COLOR_PRIMARY_3, TextDecoration.BOLD)
                if (event.pingType == ServerListPingType.OPEN_TO_LAN || (event.connection?.protocolVersion ?: 0) < 713)
                    +title
                else
                    +title.withGradient(BRAND_COLOR_PRIMARY_1, BRAND_COLOR_PRIMARY_3)

                +(" [" withColor NamedTextColor.DARK_GRAY)
                if (Environment.current is Environment.DevelopmentEnvironment) {
                    +("Dev on ${InetAddress.getLocalHost().hostName}" withColor NamedTextColor.RED)
                } else {
                    +(event.responseData.version withColor NamedTextColor.GREEN)
                }
                +("]" withColor NamedTextColor.DARK_GRAY)
            }.center(92)

            val subtitle = buildComponent {
                if (event.connection != null && event.connection!!.protocolVersion < MinecraftServer.PROTOCOL_VERSION) {
                    +("Update to Minecraft ${MinecraftServer.VERSION_NAME} to join BlueDragon." withColor NamedTextColor.RED)
                    return@buildComponent
                }
                +SERVER_NEWS
            }.center(92)
            if (event.pingType == ServerListPingType.OPEN_TO_LAN) {
                event.responseData.description = title
            } else {
                event.responseData.description = title + Component.newline() + subtitle
            }
            event.responseData.favicon = FAVICON
        }
    }
}