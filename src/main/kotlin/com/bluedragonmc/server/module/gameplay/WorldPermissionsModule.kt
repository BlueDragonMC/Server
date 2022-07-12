package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent

/**
 * This module can prevent the player from placing, breaking, or interacting with blocks in the world.
 * Note: Denying `allowBlockInteract` will also prevent players from placing blocks, even if `allowBlockPlace` is true.
 */
class WorldPermissionsModule(
    var allowBlockBreak: Boolean = false,
    var allowBlockPlace: Boolean = false,
    var allowBlockInteract: Boolean = false,
    var allowBreakMap: Boolean = false
) : GameModule() {

    private val playerPlacedBlocks = mutableListOf<Point>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            event.isCancelled = !allowBlockBreak

            if (allowBreakMap) return@addListener
            if (playerPlacedBlocks.contains(event.blockPosition)) playerPlacedBlocks.remove(event.blockPosition)
            else {
                event.player.sendMessage(
                    Component.text(
                        "You can only break blocks placed by a player!",
                        NamedTextColor.RED
                    )
                )
                event.isCancelled = true
            }
        }
        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            event.isCancelled = !allowBlockPlace
        }
        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            event.isCancelled = !allowBlockInteract

            if (allowBreakMap) return@addListener
            if (event.instance.getBlock(event.blockPosition).isAir) event.isCancelled = true
            playerPlacedBlocks.add(event.blockPosition)
        }
    }
}