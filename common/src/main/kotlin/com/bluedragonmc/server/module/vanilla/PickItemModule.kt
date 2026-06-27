package com.bluedragonmc.server.module.vanilla

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.MinecraftServer
import net.minestom.server.codec.Transcoder
import net.minestom.server.component.DataComponent
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerPickBlockEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.PlayerInventory
import net.minestom.server.item.ItemStack
import net.minestom.server.item.component.TypedCustomData
import net.minestom.server.registry.RegistryTranscoder

/**
 * Enables the vanilla "pick block" functionality (middle click)
 *
 * [See Documentation](https://developer.bluedragonmc.com/modules/pickitemmodule/)
 */
class PickItemModule : GameModule() {
    override fun initialize(
        parent: Game,
        eventNode: EventNode<Event>
    ) {
        // If a player places a block with block entity data, add the data to the new block
        // (this should probably be part of minestom or the block handlers)
        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val blockItem = event.player.getItemInHand(event.hand)
            val blockEntityData = blockItem.get(DataComponents.BLOCK_ENTITY_DATA)
            if (blockEntityData != null) {
                event.block = event.block.withNbt(blockEntityData.nbt)
            }
        }

        eventNode.addListener(PlayerPickBlockEvent::class.java) { event ->
            val block = event.instance.getBlock(event.blockPosition)
            if (block.isAir) return@addListener
            val inventory = event.player.inventory
            val includeData = event.isIncludeData && event.player.gameMode == GameMode.CREATIVE
            if (inventory.getItemStack(event.player.heldSlot.toInt()).compareBlock(block, includeData)) {
                // If the player is already holding a matching block, do nothing
                return@addListener
            }

            // If the item is already in the hotbar, swap to it
            for (slot in 0..8) {
                if (inventory.getItemStack(slot).compareBlock(block, includeData)) {
                    event.player.setHeldItemSlot(slot.toByte())
                    return@addListener
                }
            }

            // If the item is elsewhere in the inventory, move it to the player's hand
            for (slot in 9 until inventory.size) {
                if (inventory.getItemStack(slot).compareBlock(block, includeData)) {
                    val newSlot = inventory.getEmptyHotbarSlot(default = event.player.heldSlot.toInt())
                    inventory.swap(slot, newSlot)
                    event.player.setHeldItemSlot(slot.toByte())
                    return@addListener
                }
            }

            // If the player is in creative, give them a new item stack
            if (event.player.gameMode == GameMode.CREATIVE) {
                val material = block.registry()!!.material() ?: return@addListener
                val itemStack = ItemStack.builder(material).apply {
                    val blockEntityType = block.registry()!!.blockEntityType()
                    if (includeData && blockEntityType != null) {
                        set(DataComponents.BLOCK_ENTITY_DATA, TypedCustomData(blockEntityType, block.nbtOrEmpty()))
                        block.nbtOrEmpty().forEach { nbt ->
                            val coder = RegistryTranscoder(Transcoder.NBT, MinecraftServer.process())
                            val component = DataComponent.fromKey("minecraft:${nbt.key.lowercase()}") as DataComponent<Any>?
                            if (component == null) {
                                logger.warn("Invalid block data component: ${nbt.key}")
                                return@forEach
                            }
                            component.decode(coder, nbt.value).mapResult { result: Any ->
                                set(component, result)
                            }
                        }
                    }
                }.build()
                val slot = inventory.getEmptyHotbarSlot(default = event.player.heldSlot.toInt())
                inventory.setItemStack(slot, itemStack)
                event.player.setHeldItemSlot(slot.toByte())
            }
        }
    }
}

private fun ItemStack.compareBlock(block: Block, includeData: Boolean): Boolean {
    return if (includeData) {
        compareBlock(block, false) &&
                get(DataComponents.BLOCK_ENTITY_DATA)?.nbt == block.nbt()
    } else {
        material().block()?.compare(block, Block.Comparator.ID) == true
    }
}
private fun PlayerInventory.getEmptyHotbarSlot(default: Int): Int {
    for (slot in 0 .. 8) {
        if (getItemStack(slot).isAir) return slot
    }
    return default
}
private fun PlayerInventory.swap(slot1: Int, slot2: Int) {
    val item1 = getItemStack(slot1)
    val item2 = getItemStack(slot2)
    setItemStack(slot1, item2)
    setItemStack(slot2, item1)
}