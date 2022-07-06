package com.bluedragonmc.server.module.ai

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import net.minestom.server.entity.fakeplayer.FakePlayer
import net.minestom.server.entity.fakeplayer.FakePlayerOption
import net.minestom.server.event.entity.EntityTickEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import org.jetbrains.kotlinx.dl.api.core.Sequential
import org.jetbrains.kotlinx.dl.api.core.layer.core.Dense
import org.jetbrains.kotlinx.dl.api.core.layer.core.Input
import org.jetbrains.kotlinx.dl.api.core.layer.reshaping.Flatten
import org.jetbrains.kotlinx.dl.api.core.loss.Losses
import org.jetbrains.kotlinx.dl.api.core.metric.Metrics
import org.jetbrains.kotlinx.dl.api.core.optimizer.Adam
import org.jetbrains.kotlinx.dl.api.core.summary.printSummary
import java.util.*
import kotlin.math.pow

class MLAgent(private val instance: Instance) {

    private val memorySize = 2000

    companion object {
        var count = 0

        val actions = listOf<(Entity) -> Unit>({
            // Move left
            TODO()
        }, {
            // Move right
            TODO()
        }, {
            // Move forward
            TODO()
        }, {
            // Move backward
            TODO()
        }, {
            // Jump
            TODO()
        }, {
            // Attack
            TODO()
        })
    }

    private var currentAction: ((Entity) -> Unit)? = null
    private fun processAction() = currentAction?.invoke(this.entity)
    private val cachedModel by lazy { getModel() }

    init {
        FakePlayer.initPlayer(UUID.randomUUID(),
            "AI Agent ${++count}",
            FakePlayerOption().setRegistered(false).setInTabList(true)) {
            entity = it
            entity.eventNode().addListener(EntityTickEvent::class.java) { event ->
                if (event.entity.aliveTicks % 4 == 0L) { // Recalculate the entity's action 5 times per second
                    currentAction = actions[cachedModel.predict(getInputs().toFloatArray())]
                }
                processAction()
            }
            it.setInstance(instance)
        }
    }

    private lateinit var entity: FakePlayer
    private val r = 3 // The radius around the player to input nearby block states
    private val inputSize
        get() = 4 + (r * 2 + 1).cubed() // 3 inputs for the nearby player's position and one for the player's health, plus the cube of nearby block states.

    class Memory // TODO fields here

    private val pastMemories = ArrayDeque<Memory>(memorySize)

    fun remember(memory: Memory) {
        pastMemories.add(memory)
        if (pastMemories.size > memorySize) pastMemories.pop()
    }

    private fun getModel(): Sequential {
        val model = Sequential.of(
            Input(inputSize.toLong(), name = "input"),
            Flatten(name = "flatten_1"),
            Dense(32, name = "hidden_1"),
            Dense(32, name = "hidden_2"),
            Dense(6, name = "output"),
        )

        model.printSummary()

        model.compile(Adam(), Losses.BINARY_CROSSENTROPY, Metrics.ACCURACY)

        return model
    }

    private fun getInputs(): List<Float> {
        val agentPos = entity.position
        val nearbyPlayerPos =
            instance.getNearbyEntities(entity.position, r + 1.0).firstOrNull { it is Player }?.position?.sub(agentPos)
                ?: Pos.ZERO
        val health = entity.health / entity.maxHealth
        // map the blocks around the player as binary, 1 or 0 depending on if there is a block there or not.
        val nearbyBlocksData = (agentPos.blockX() radius r).map { x ->
            (agentPos.blockY() radius r).map { y ->
                (agentPos.blockZ() radius r).map { z ->
                    if (instance.getBlock(x, y, z) == Block.AIR) 0.0f else 1.0f
                }
            }
        }.flatten().flatten()
        return listOf(listOf(nearbyPlayerPos.x.toFloat(),
            nearbyPlayerPos.y.toFloat(),
            nearbyPlayerPos.z.toFloat(),
            health), nearbyBlocksData).flatten()
    }

    fun disconnect() {
        entity.remove()
    }

    private infix fun Int.radius(radius: Int): IntRange {
        return (this - radius)..(this + radius)
    }

    private fun Int.cubed() = toDouble().pow(3.0).toInt()
}
