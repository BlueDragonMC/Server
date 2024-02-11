package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.item.PickupItemEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.tag.Tag
import java.util.*

class CustomItemModule(vararg val items: CustomItem) : GameModule() {

    companion object {
        private val CUSTOM_UUID_TAG = Tag.String("custom_item_uid")
    }
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        items.forEach { registerItem(it) }
    }

    fun registerItem(item: CustomItem) {
        eventNode.addChild(item.eventNode)
    }

    fun giveItem(player: Player, item: CustomItem) {
        item.onObtain(player)
        player.inventory.addItemStack(item.getItemStack())
    }

    fun giveItem(player: Player, item: CustomItem, amount: Int) {
        item.onObtain(player)
        player.inventory.addItemStack(item.getItemStack().withAmount(amount))
    }

    fun giveItem(player: Player, uid: String) = giveItem(player, getCustomItem(uid))

    fun giveItem(player: Player, uid: String, amount: Int) = giveItem(player, getCustomItem(uid), amount)

    fun getCustomItemOrNull(uid: String): CustomItem? {
        for (i in items) {
            if (i.uid == uid) return i
        }
        return null
    }

    fun getCustomItem(uid: String): CustomItem = getCustomItemOrNull(uid) ?: error("No custom item matches the given uid!")

    /**
     * Returns the [CustomItem] that the given `itemStack` is an instance of, or `null` if this item does not represent a CustomItem.
     */
    fun getCustomItemOrNull(itemStack: ItemStack): CustomItem? {
        val uid = itemStack.getTag(CUSTOM_UUID_TAG) ?: return null // itemStack is not a custom item
        for (i in items) {
            if (uid == i.uid) return i
        }
        return null
    }

    /**
     * Returns the [CustomItem] that the given `itemStack` is an instance of, or throws an error if this item does not represent a CustomItem.
     */
    fun getCustomItem(itemStack: ItemStack) = getCustomItemOrNull(itemStack) ?: error("No custom item matches the given itemStack!")
    override fun toString(): String {
        return "CustomItemModule(startingItems=${items.contentToString()})"
    }

    abstract class CustomItem {
        /**
         * A unique value that identifies all items of this type.
         */
        abstract val uid: String

        protected abstract fun createItemStack(): ItemStack

        fun getItemStack(): ItemStack {
            return createItemStack().withTag(CUSTOM_UUID_TAG, uid)
        }

        val eventNode by lazy {
            // Create an event node that only receives events related to this item
            val eventNode = EventNode.event("custom-item-${UUID.randomUUID()}-$uid", EventFilter.ITEM) { event ->
                event.itemStack.getTag(CUSTOM_UUID_TAG) == uid
            }

            eventNode.addListener(net.minestom.server.event.player.PlayerUseItemEvent::class.java) { event ->
                onUse(event)
            }
            eventNode.addListener(ItemDropEvent::class.java) { event ->
                onDrop(event)
            }
            eventNode.addListener(PickupItemEvent::class.java) { event ->
                onPickup(event)
            }

            return@lazy eventNode
        }

        protected open fun onUse(event: PlayerUseItemEvent) {}
        protected open fun onDrop(event: ItemDropEvent) {}
        protected open fun onPickup(event: PickupItemEvent) {}

        /**
         * Called when a player obtains this item from a [giveItem] call.
         */
        open fun onObtain(player: Player) {}

        override fun toString(): String {
            return "CustomItem(uid='$uid')"
        }
    }
}