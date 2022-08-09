package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block

/**
 * This module can prevent the player from placing, breaking, or interacting with blocks in the world.
 * Note: Denying `allowBlockInteract` will also prevent players from placing blocks, even if `allowBlockPlace` is true.
 */
class WorldPermissionsModule(
    var allowBlockBreak: Boolean = false,
    var allowBlockPlace: Boolean = false,
    var allowBlockInteract: Boolean = false,
    var allowBreakMap: Boolean = false,
    val exceptions: List<Block> = listOf()
) : GameModule() {

    private val playerPlacedBlocks = mutableListOf<Point>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (exceptions.contains(event.block)) return@addListener
            event.isCancelled = !allowBlockBreak

            if (allowBlockBreak && !allowBreakMap) {
                if (playerPlacedBlocks.contains(event.blockPosition)) {
                    playerPlacedBlocks.remove(event.blockPosition)
                } else {
                    parent.callCancellable(PreventPlayerBreakMapEvent(event.player, event.block, event.resultBlock, event.blockPosition)) {
                        event.player.sendMessage(
                            Component.translatable("module.world_permissions.break_world", NamedTextColor.RED)
                        )
                        event.isCancelled = true
                    }
                }
            }
        }
        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (exceptions.contains(event.block)) return@addListener
            event.isCancelled = !allowBlockPlace

            if (!event.instance.getBlock(event.blockPosition).isAir) event.isCancelled = true

            if (!allowBreakMap) playerPlacedBlocks.add(event.blockPosition)
        }
        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (exceptions.contains(event.block)) return@addListener
            event.isCancelled = !allowBlockInteract
        }
    }

    /**
     * Called when a player breaks a non-player-placed block when they are not supposed to be allowed to.
     * If this event is cancelled, the player will be allowed to break the block.
     */
    class PreventPlayerBreakMapEvent(player: Player, block: Block, resultBlock: Block, blockPosition: Point) : PlayerBlockBreakEvent(player, block, resultBlock, blockPosition)
}