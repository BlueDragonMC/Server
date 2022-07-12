package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.hologram.Hologram
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

    fun addGenerator(instance: Instance, locations: Collection<Pos>, items: Map<ItemStack, Int>, hasHologram: Boolean = items.size == 1) {
        locations.forEach { pos ->
            addGenerator(ItemGenerator(instance, pos, items, hasHologram))
        }
    }

    fun addGenerator(generator: ItemGenerator) {
        generators.add(generator)
        generator.countdownTasks.forEach { tasks.add(it.schedule()) }
    }

    override fun deinitialize() {
        tasks.forEach { it.cancel() }
        generators.forEach { it.removeHolograms() }
    }

    data class ItemGenerator(val instance: Instance, val location: Pos, val items: Map<ItemStack, Int>, val hasHologram: Boolean = items.size == 1) {
        val countdownTasks = mutableListOf<Task.Builder>()
        private lateinit var hologram: Hologram
        private lateinit var staticHologram: Hologram
        private var secondsLeft: Int = 0

        init {
            items.forEach { (itemStack, interval) ->
                countdownTasks.add(MinecraftServer.getSchedulerManager().buildTask {
                    val item = ItemEntity(itemStack)
                    item.setInstance(instance, location)
                }.repeat(Duration.ofSeconds(interval.toLong())))
            }
            if(hasHologram) {
                // Create a static hologram showing the kind of generator
                staticHologram = Hologram(instance, location.add(0.0, 2.0, 0.0),
                    Component.translatable(items.keys.first().material().registry().translationKey(), ALT_COLOR_1) +
                            Component.text(" Generator", ALT_COLOR_1)
                )
                // Create a dynamic hologram that updates every second
                hologram = Hologram(instance, location.add(0.0, 1.75, 0.0), Component.empty())
                countdownTasks.add(MinecraftServer.getSchedulerManager().buildTask {
                    secondsLeft --
                    if(secondsLeft <= 0) secondsLeft = items.values.first()
                    updateHologram()
                }.repeat(Duration.ofSeconds(1)))
                updateHologram()
            }
        }

        internal fun removeHolograms() {
            if(hasHologram) {
                staticHologram.remove()
                hologram.remove()
            }
        }

        private fun updateHologram() {
            hologram.text = Component.text("Spawning in ") + Component.text("${secondsLeft}s")
        }
    }
}