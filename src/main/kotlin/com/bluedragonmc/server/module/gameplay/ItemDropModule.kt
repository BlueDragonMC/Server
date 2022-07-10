package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.time.Duration

/**
 * Allows players to drop items, and optionally drops all their items on death.
 * Also allows blocks be dropped when broken.
 */
class ItemDropModule(var dropBlocksOnBreak: Boolean = true, var dropAllOnDeath: Boolean = false) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(ItemDropEvent::class.java) { event ->
            dropItem(event.itemStack, event.instance, event.player.position.add(0.0, 1.0, 0.0))
            // TODO add velocity to the item
        }
        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            if (dropAllOnDeath) {
                for (item in event.entity.inventory.itemStacks) {
                    dropItem(item, event.instance, event.entity.position)
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

    fun dropItem(item: ItemStack, instance: Instance, position: Pos): ItemEntity {
        val itemEntity = ItemEntity(item)
        itemEntity.setInstance(instance, position)
        itemEntity.isMergeable = false
        itemEntity.isPickable = false
        MinecraftServer.getSchedulerManager().buildTask {
            itemEntity.isMergeable = true
            itemEntity.isPickable = true
        }.delay(Duration.ofMillis(300)).schedule()
        return itemEntity
    }
}