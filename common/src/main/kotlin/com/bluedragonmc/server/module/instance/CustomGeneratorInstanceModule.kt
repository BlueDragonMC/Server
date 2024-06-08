package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.NAMESPACE
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance
import net.minestom.server.instance.generator.Generator
import net.minestom.server.registry.DynamicRegistry
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType

class CustomGeneratorInstanceModule(
    private val dimensionType: DynamicRegistry.Key<DimensionType> = DimensionType.OVERWORLD,
    private val generator: Generator,
) : InstanceModule() {
    private lateinit var instance: Instance

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        instance = MinecraftServer.getInstanceManager().createInstanceContainer(dimensionType)
        instance.setGenerator(generator)
    }

    override fun ownsInstance(instance: Instance): Boolean {
        return instance == this.instance
    }

    override fun getSpawningInstance(player: Player): Instance = instance

    fun getInstance() = instance

    companion object {

        init {
            val id = NamespaceID.from("$NAMESPACE:fullbright_dimension")
            if (MinecraftServer.getDimensionTypeRegistry().get(id) == null) {
                MinecraftServer.getDimensionTypeRegistry().register(
                    DimensionType.builder(id).ambientLight(1.0F).build()
                )
            }
        }

        fun getFullbrightDimension(): DynamicRegistry.Key<DimensionType> =
            DynamicRegistry.Key.of(NamespaceID.from("$NAMESPACE:fullbright_dimension"))
    }
}
