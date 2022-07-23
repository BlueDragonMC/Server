package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.MapData
import com.bluedragonmc.server.utils.hoverEvent
import com.bluedragonmc.server.utils.surroundWithSeparators
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent

/**
 * Displays a message to players when they join the game.
 */
class MOTDModule(val motd: Component) : GameModule() {

    override val dependencies = listOf(DatabaseModule::class)

    private lateinit var mapData: MapData
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        mapData = MapData(parent.mapName)
        val databaseModule = parent.getModule<DatabaseModule>()
        DatabaseModule.IO.launch {
            mapData = databaseModule.getMapOrNull(parent.mapName) ?: MapData(parent.mapName)
        }
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.sendMessage(
                Component.text(parent.name + "\n", BRAND_COLOR_PRIMARY_1, TextDecoration.BOLD)
                    .append(motd.color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                    .append(Component.text("\nMap: ", BRAND_COLOR_PRIMARY_2).decoration(TextDecoration.BOLD, false))
                    .append(Component.text(parent.mapName, BRAND_COLOR_PRIMARY_1, TextDecoration.BOLD).hoverEvent(mapData.description, NamedTextColor.WHITE))
                    .append(Component.text(" by ", BRAND_COLOR_PRIMARY_2).decoration(TextDecoration.BOLD, false))
                    .append(Component.text(mapData.author, BRAND_COLOR_PRIMARY_1).decoration(TextDecoration.BOLD, false))
                    .surroundWithSeparators()
            )
        }
    }
}