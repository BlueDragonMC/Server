package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.event.trait.PlayerEvent

class ActionBarModule(
    private val interval: Int = 2,
    private val separator: Component = Component.text(" | ", NamedTextColor.DARK_GRAY),
) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerTickEvent::class.java) { event ->
            if (event.entity.aliveTicks % interval == 0L) {
                val actionBar = collectActionBar(parent, event.player)
                event.player.sendActionBar(actionBar)
            }
        }
    }

    private fun collectActionBar(parent: Game, player: Player): Component {
        val event = CollectActionBarEvent(player)
        parent.callEvent(event)
        val items = event.getItems()
        if (items.isEmpty()) return Component.empty()
        return Component.join(JoinConfiguration.separator(separator), items)
    }

    data class CollectActionBarEvent(
        private val player: Player,
        private val items: MutableList<Component> = mutableListOf(),
    ) : PlayerEvent {
        override fun getPlayer(): Player = player

        fun addItem(item: Component) {
            items.add(item)
        }

        fun getItems() = items.toList()
    }
}