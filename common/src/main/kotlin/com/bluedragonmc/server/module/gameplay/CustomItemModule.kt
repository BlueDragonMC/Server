package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.displayName
import com.bluedragonmc.server.utils.manage
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.item.PickupItemEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.event.player.PlayerStartDiggingEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.event.trait.ItemEvent
import net.minestom.server.event.trait.PlayerInstanceEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.component.UseCooldown
import net.minestom.server.network.packet.server.play.SetCooldownPacket
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import java.time.Duration

class CustomItemModule(vararg val items: CustomItem) : GameModule() {

    companion object {
        private val CUSTOM_UUID_TAG = Tag.String("custom_item_uid")
    }

    private lateinit var parent: Game

    /**
     * Each registered item has a map containing the last time each player used the item.
     * Players are only in the map if their use of the item is currently on cooldown.
     */
    private val cooldownPlayers = mutableMapOf<CustomItem, MutableMap<Player, Long>>()
    private val eventNodes = mutableMapOf<CustomItem, EventNode<ItemEvent>>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        items.forEach { registerItem(it) }

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            val customItem = getCustomItemOrNull(event.itemStack) ?: return@addListener
            val itemEventNode = eventNodes[customItem]
            if (itemEventNode == null) {
                logger.warn("A player tried to use a custom item (${customItem.uid}) before it was registered!")
                return@addListener
            }
            if (cooldownPlayers[customItem]?.containsKey(event.player) == false) {
                itemEventNode.call(PlayerUseCustomItemEvent(event.player, event.hand, event.itemStack, event.itemUseTime))
                if (customItem.cooldown != Duration.ZERO) {
                    setCooldownRemaining(event.player, customItem, customItem.cooldown)
                }
            }
        }

        eventNode.addListener(PlayerHandAnimationEvent::class.java) { event ->
            val customItem = getCustomItemOrNull(event.player.getItemInHand(event.hand)) ?: return@addListener
            if (event.player.gameMode != GameMode.ADVENTURE) {
                val targetBlock =
                    event.player.getTargetBlockPosition(event.player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).value.toInt())
                // left-click will get picked up by start digging event if player is looking at a block
                if (targetBlock != null) return@addListener
            }
            event.isCancelled = customItem.onClick(event)
        }

        eventNode.addListener(PlayerStartDiggingEvent::class.java) { event ->
            val customItem = getCustomItemOrNull(event.player.itemInMainHand) ?: return@addListener
            event.isCancelled = customItem.onClick(event)
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val customItem = getCustomItemOrNull(event.player.itemInMainHand) ?: return@addListener
            event.isCancelled = customItem.onClick(event)
        }
    }

    fun registerItem(item: CustomItem) {
        cooldownPlayers[item] = mutableMapOf()

        val itemEventNode = EventNode.event("custom-item-${parent.id}-${item.uid}", EventFilter.ITEM) { event ->
            event.itemStack.getTag(CUSTOM_UUID_TAG) == item.uid
        }

        itemEventNode.addListener(PlayerUseCustomItemEvent::class.java) { event ->
            item.onUse(event)
        }
        itemEventNode.addListener(ItemDropEvent::class.java) { event ->
            item.onDrop(event)
        }
        itemEventNode.addListener(PickupItemEvent::class.java) { event ->
            item.onPickup(event)
        }

        eventNodes[item] = itemEventNode
        eventNode.addChild(itemEventNode)
    }

    fun giveItem(player: Player, item: CustomItem) {
        item.onObtain(player)
        player.inventory.addItemStack(item.itemStack)
    }

    fun giveItem(player: Player, item: CustomItem, amount: Int) {
        item.onObtain(player)
        player.inventory.addItemStack(item.itemStack.withAmount(amount))
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
        return getCustomItemOrNull(uid)
    }

    /**
     * Returns the [CustomItem] that the given `itemStack` is an instance of, or throws an error if this item does not represent a CustomItem.
     */
    fun getCustomItem(itemStack: ItemStack) = getCustomItemOrNull(itemStack) ?: error("No custom item matches the given itemStack!")

    fun setCooldownRemaining(player: Player, item: CustomItem, duration: Duration) {
        if (duration == Duration.ZERO) {
            cooldownPlayers[item]!!.remove(player)
            player.sendPacket(SetCooldownPacket(item.cooldownGroup, 0))
            item.onCooldownEnd(player)
        } else {
            val durationMillis = duration.toMillis()
            cooldownPlayers[item]!![player] = System.currentTimeMillis() + durationMillis - item.cooldown.toMillis()
            player.sendPacket(SetCooldownPacket(item.cooldownGroup, (durationMillis * 0.02).toInt()))
            MinecraftServer.getSchedulerManager().buildTask {
                setCooldownRemaining(player, item, Duration.ZERO)
            }.delay(duration).schedule().manage(parent)
        }
    }

    fun getCooldownRemaining(player: Player, item: CustomItem): Duration {
        return Duration.ofMillis(
            item.cooldown.toMillis() + (cooldownPlayers[item]!![player] ?: return Duration.ZERO) - System.currentTimeMillis()
        )
    }

    override fun toString(): String {
        return "CustomItemModule(startingItems=${items.contentToString()})"
    }

    /**
     * Called when a player uses the custom item and they are not on cooldown.
     */
    private class PlayerUseCustomItemEvent(player: Player, hand: PlayerHand, itemStack: ItemStack, itemUseTime: Long) :
        PlayerUseItemEvent(player, hand, itemStack, itemUseTime)

    abstract class CustomItem {
        /**
         * A unique value that identifies all items of this type.
         */
        abstract val uid: String
        open val cooldown: Duration = Duration.ZERO

        internal val cooldownGroup = "bluedragon:custom-item-$uid"

        protected abstract fun createItemStack(): ItemStack

        val itemStack: ItemStack by lazy { createItemStack().with { builder ->
            builder.setTag(CUSTOM_UUID_TAG, uid)
            if (cooldown != Duration.ZERO) {
                builder.set(DataComponents.USE_COOLDOWN, UseCooldown(cooldown.seconds + cooldown.nano / 1_000_000_000f, cooldownGroup))
            }
        }}

        open fun onUse(event: PlayerUseItemEvent) {}
        open fun onDrop(event: ItemDropEvent) {}
        open fun onPickup(event: PickupItemEvent) {}
        open fun onClick(event: PlayerInstanceEvent): Boolean = false

        /**
         * Called when a player is no longer on cooldown for this item.
         * The default implementation sends a chat message and a sound.
         */
        open fun onCooldownEnd(player: Player) {
            val name = itemStack.get(DataComponents.ITEM_NAME) ?: itemStack.material().displayName(BRAND_COLOR_PRIMARY_1)
            player.sendMessage(Component.translatable("module.customitem.ready", BRAND_COLOR_PRIMARY_2, name))

            player.playSound(
                Sound.sound(
                    SoundEvent.BLOCK_NOTE_BLOCK_PLING,
                    Sound.Source.PLAYER,
                    1.0f,
                    2.0f
                )
            )
        }

        /**
         * Called when a player obtains this item from a [giveItem] call.
         */
        open fun onObtain(player: Player) {}

        override fun toString(): String {
            return "CustomItem(uid='$uid')"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CustomItem) return false

            if (uid != other.uid) return false

            return true
        }

        override fun hashCode(): Int {
            return uid.hashCode()
        }
    }
}