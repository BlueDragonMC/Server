package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.Game
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance
import net.minestom.server.instance.generator.Generator
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType

class CustomGeneratorInstanceModule(
    val dimensionType: DimensionType = DimensionType.OVERWORLD, val generator: Generator
) : InstanceModule() {
    private lateinit var instance: Instance
    override fun getInstance(): Instance = instance

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        instance = MinecraftServer.getInstanceManager().createInstanceContainer(dimensionType)
        instance.setGenerator(generator)
    }

    companion object {
        fun getFullbrightDimension() = MinecraftServer.getDimensionTypeManager().getDimension(
            NamespaceID.from("bluedragon:fullbright_dimension")
        )!!
    }
}
