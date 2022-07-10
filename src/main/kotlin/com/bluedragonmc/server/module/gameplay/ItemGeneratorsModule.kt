package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.timer.Task
import java.time.Duration

/**
 * A module that allows item generators to be added to the game.
 * Item generators spawn items at certain intervals. They are a popular feature in BedWars and some other games.
 */
class ItemGeneratorsModule(generators: MutableList<ItemGenerator> = mutableListOf()) : GameModule() {

    private val generators = mutableListOf<ItemGenerator>()
    private val tasks = mutableListOf<Task>()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        generators.forEach {
            addGenerator(it)
        }
    }

    fun addGenerator(generator: ItemGenerator) {
        generators.add(generator)
        generator.countdownTasks.forEach { tasks.add(it.schedule()) }
    }

    override fun deinitialize() {
        tasks.forEach { it.cancel() }
    }

    data class ItemGenerator(val instance: Instance, val location: Pos, val items: Map<ItemStack, Int>) {
        val countdownTasks = mutableListOf<Task.Builder>()
        init {
            items.forEach {
                countdownTasks.add(MinecraftServer.getSchedulerManager().buildTask {
                    val item = ItemEntity(it.key)
                    item.setInstance(instance, location)
                }.repeat(Duration.ofSeconds(it.value.toLong())))
            }
        }
    }
}