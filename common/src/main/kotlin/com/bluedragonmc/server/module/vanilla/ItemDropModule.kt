package com.bluedragonmc.server.module.vanilla

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.trait.BlockEvent
import net.minestom.server.event.trait.CancellableEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
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

    /**
     * Called when a block is broken before it is dropped in item form in the world.
     * Can be used to change the ItemStack or
     */
    data class BlockItemDropEvent(private val instance: Instance, private val block: Block, private var blockPosition: BlockVec, var itemStack: ItemStack) :
        BlockEvent, InstanceEvent, CancellableEvent {
        override fun getBlock() = block
        override fun getBlockPosition() = blockPosition

        fun setBlockPosition(pos: BlockVec) {
            this.blockPosition = pos
        }

        override fun getInstance() = instance

        private var isCancelled = false

        override fun isCancelled() = isCancelled
        override fun setCancelled(cancel: Boolean) {
            this.isCancelled = cancel
        }
    }

    private val excludedBlocks = listOf<Block>(
        *Block.values().filter { it.name().contains("bed") || it.name().contains("leaves") }.toTypedArray(),
        Block.SHORT_GRASS,
        Block.TALL_GRASS,
        Block.VINE,
        Block.COBWEB
    )

    private lateinit var parent: Game

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
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
            if (dropBlocksOnBreak && !event.isCancelled && !excludedBlocks.contains(event.block)) {
                val itemStack = ItemStack.of(event.block.registry().material() ?: Material.AIR, 1)
                val dropEvent = BlockItemDropEvent(event.instance, event.block, event.blockPosition, itemStack)
                parent.callCancellable(dropEvent) {
                    dropItem(
                        dropEvent.itemStack,
                        dropEvent.instance,
                        Pos(dropEvent.blockPosition.x(), dropEvent.blockPosition.y(), dropEvent.blockPosition.z())
                    )
                }
            }
        }
    }

    private fun dropItemFromPlayer(item: ItemStack, instance: Instance, player: Player, throwRandomly: Boolean) {
        val itemEntity = ItemEntity(item)
        itemEntity.setInstance(instance, player.position.add(0.0, player.eyeHeight - 0.3, 0.0))
        itemEntity.setPickupDelay(2, ChronoUnit.SECONDS)
        if (throwRandomly) {
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