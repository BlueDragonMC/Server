package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.surroundWithSeparators
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
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.sendMessage(Component.text(parent.name + "\n", NamedTextColor.YELLOW, TextDecoration.BOLD).append(motd.color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)).surroundWithSeparators())
        }
    }
}