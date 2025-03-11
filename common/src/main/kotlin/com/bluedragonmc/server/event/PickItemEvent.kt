package com.bluedragonmc.server.event

import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerInstanceEvent

sealed class PickItemEvent {
    /**
     * Called when a player uses the Pick Item shortcut (middle-click) on a block.
     */
    data class Block(private val player: Player, val pos: Point, val includeData: Boolean) : PickItemEvent(), PlayerInstanceEvent {
        override fun getPlayer() = this.player
    }

    /**
     * Called when a player uses the Pick Item shortcut (middle-click) on an entity.
     */
    data class Entity(private val player: Player, val target: net.minestom.server.entity.Entity?, val includeData: Boolean) : PickItemEvent(), PlayerInstanceEvent {
        override fun getPlayer() = this.player
    }
}