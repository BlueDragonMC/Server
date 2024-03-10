package com.bluedragonmc.server.module.vanilla

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Allows players to drop items, and optionally drops all their items on death.
 * Also allows blocks be dropped when broken.
 */
class ItemDropModule(var dropBlocksOnBreak: Boolean = true, var dropAllOnDeath: Boolean = false) : GameModule() {

    override val eventPriority: Int
        get() = 999 // Higher numbers run last; this module needs to receive events late to allow for cancellations from other modules

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(ItemDropEvent::class.java) { event ->
            dropItemFromPlayer(event.itemStack, event.instance, event.player, false)
        }
        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            if (dropAllOnDeath) {
                for (item in event.entity.inventory.itemStacks) {
                    dropItemFromPlayer(item, event.instance, event.entity, true)
                }
                event.entity.inventory.clear()
            }
        }
        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (dropBlocksOnBreak && !event.isCancelled && !event.block.name().contains("bed")) {
                dropItem(
                    ItemStack.of(event.block.registry().material() ?: Material.AIR, 1),
                    event.instance,
                    Pos(event.blockPosition.x(), event.blockPosition.y(), event.blockPosition.z())
                )
            }
        }
    }

    private fun dropItemFromPlayer(item: ItemStack, instance: Instance, player: Player, throwRandomly: Boolean) {
        val itemEntity = ItemEntity(item)
        itemEntity.setInstance(instance, player.position.add(0.0, player.eyeHeight - 0.3, 0.0))
        itemEntity.setPickupDelay(2, ChronoUnit.SECONDS)
        if(throwRandomly) {
            val multiplier = Random.nextFloat() * 4f
            val angle = Random.nextFloat() * Math.PI * 2f
            itemEntity.velocity = Vec(-sin(angle) * multiplier, 0.2, cos(angle) * multiplier)
        } else {
            itemEntity.velocity = player.position.direction().mul(5.0)
        }
    }

    fun dropItem(item: ItemStack, instance: Instance, position: Pos) {
        val itemEntity = ItemEntity(item)
        itemEntity.setPickupDelay(300, ChronoUnit.MILLIS)

        // Apply random velocity because we don't have a yaw or pitch
        val multiplier = Random.nextFloat() * 4f
        val angle = Random.nextFloat() * Math.PI * 2f
        itemEntity.velocity = Vec(-sin(angle) * multiplier, 0.2, cos(angle) * multiplier)

        itemEntity.setInstance(instance, position.add(0.5, 0.5, 0.5))
    }
}