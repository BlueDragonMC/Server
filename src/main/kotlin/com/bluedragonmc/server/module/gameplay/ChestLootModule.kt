package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.coordinate.Point
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.item.ItemStack
import kotlin.random.Random

class ChestLootModule(private val lootProvider: ChestLootProvider) : GameModule() {

    override val dependencies = listOf(ChestModule::class)

    interface ChestLootProvider {
        fun getLoot(chestLocation: Point): Collection<ItemStack>
    }

    /**
     * Provides loot using a different provider based on the chest's location.
     * This can be used to give a separate list of items based on where the chest is located.
     * For example, SkyWars contains chests near the middle of the map which contain higher-value items.
     */
    class LocationBasedLootProvider(private val provider: (Point) -> ChestLootProvider) : ChestLootProvider {
        override fun getLoot(chestLocation: Point): Collection<ItemStack> =
            provider(chestLocation).getLoot(chestLocation)
    }

    /**
     * Provides loot based on the [potentialItems]. Each item, if selected, is given a slot in the chest.
     * If a slot is already taken, the item will not be set or assigned a new slot.
     */
    class RandomLootProvider(private val potentialItems: Collection<WeightedItemStack>) : ChestLootProvider {
        data class WeightedItemStack(val itemStack: ItemStack, val chance: Float)

        override fun getLoot(chestLocation: Point): Collection<ItemStack> {
            val chest = mutableMapOf<Int, ItemStack>()
            (0..26).forEach { slot -> chest[slot] = ItemStack.AIR }
            potentialItems.filter { Random.nextFloat() <= it.chance }.forEach {
                // Find a slot for this item that has not been taken yet
                val slot = (0..26).random()
                if (!chest.containsKey(slot)) chest[slot] = it.itemStack
            }
            return chest.values
        }
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(ChestModule.ChestPopulateEvent::class.java) { event ->
            lootProvider.getLoot(event.position).forEachIndexed { index, itemStack ->
                event.menu.setItemStack(event.player, index, itemStack)
            }
        }
    }
}
