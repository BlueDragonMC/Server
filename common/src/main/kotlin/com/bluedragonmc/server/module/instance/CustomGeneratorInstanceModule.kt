package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.NAMESPACE
import net.kyori.adventure.key.Key
import net.minestom.server.MinecraftServer
import net.minestom.server.color.Color
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance
import net.minestom.server.instance.generator.Generator
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.DimensionType
import net.minestom.server.world.attribute.EnvironmentAttribute

class CustomGeneratorInstanceModule(
    private val dimensionType: RegistryKey<DimensionType> = DimensionType.OVERWORLD,
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

        private lateinit var key: RegistryKey<DimensionType>

        init {
            val id = Key.key("$NAMESPACE:fullbright_dimension")
            if (MinecraftServer.getDimensionTypeRegistry().get(id) == null) {
                key = MinecraftServer.getDimensionTypeRegistry().register(
                    id,
                    DimensionType.builder()
                        .ambientLight(1.0F)
                        .setAttribute(EnvironmentAttribute.AMBIENT_LIGHT_COLOR, Color.WHITE)
                        .setAttribute(EnvironmentAttribute.FOG_COLOR, Color(0xC0D8FF))
                        .setAttribute(EnvironmentAttribute.SKY_COLOR, Color(0x78A7FF))
                        .build()
                )
            }
        }

        fun getFullbrightDimension() = key
    }
}
