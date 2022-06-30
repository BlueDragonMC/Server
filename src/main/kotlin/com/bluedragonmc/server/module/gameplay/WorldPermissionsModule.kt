package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent

/**
 * This module can prevent the player from placing, breaking, or interacting with blocks in the world.
 * Note: Denying `allowBlockInteract` will also prevent players from placing blocks, even if `allowBlockPlace` is true.
 */
class WorldPermissionsModule(var allowBlockBreak: Boolean = false, var allowBlockPlace: Boolean = false, var allowBlockInteract: Boolean = false) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerBlockBreakEvent::class.java) {
            it.isCancelled = !allowBlockBreak
        }
        eventNode.addListener(PlayerBlockPlaceEvent::class.java) {
            it.isCancelled = !allowBlockPlace
        }
        eventNode.addListener(PlayerBlockInteractEvent::class.java) {
            it.isCancelled = !allowBlockInteract
        }
    }
}