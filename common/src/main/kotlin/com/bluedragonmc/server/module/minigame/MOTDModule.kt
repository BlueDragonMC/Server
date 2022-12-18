package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.model.MapData
import com.bluedragonmc.server.utils.buildComponent
import com.bluedragonmc.server.utils.noBold
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent

/**
 * Displays a message to players when they join the game.
 */
class MOTDModule(val motd: Component, val showMapName: Boolean = true) : GameModule() {

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        val mapData = parent.mapData ?: MapData(parent.mapName)
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.sendMessage(
                buildComponent {
                    // Game name
                    +Component.text(parent.name, BRAND_COLOR_PRIMARY_1, TextDecoration.BOLD)
                    +Component.newline()
                    +buildComponent {
                        // MOTD
                        +motd.color(NamedTextColor.WHITE)
                        if (showMapName) +Component.newline()
                        if (showMapName) +Component.translatable("module.motd.map",
                            BRAND_COLOR_PRIMARY_2,
                            // Map name
                            Component.text(parent.mapName, BRAND_COLOR_PRIMARY_1, TextDecoration.BOLD)
                                .hoverEvent(HoverEvent.showText(
                                    Component.text(parent.mapName,
                                        BRAND_COLOR_PRIMARY_1,
                                        TextDecoration.BOLD) + Component.newline() + Component.text(mapData.description,
                                        NamedTextColor.GRAY).noBold()
                                )),
                            // Map builder
                            Component.text(mapData.author, BRAND_COLOR_PRIMARY_1)
                        )
                    }.noBold()
                }.surroundWithSeparators()
            )
        }
    }
}
