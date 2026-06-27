package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.map.MapProviderModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer

/**
 * An implementation of [InstanceModule] that creates a single
 * [InstanceContainer] for each game using a map loaded by [MapProviderModule].
 *
 * [See Documentation](https://developer.bluedragonmc.com/modules/instancecontainermodule/)
 */
@DependsOn(MapProviderModule::class)
class InstanceContainerModule : InstanceModule() {

    private lateinit var instance: InstanceContainer

    override fun getSpawningInstance(player: Player): Instance = this.instance
    override fun ownsInstance(instance: Instance): Boolean = instance == this.instance

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        // Create a copy of the loaded InstanceContainer to prevent modifying the state of the original
        val mapProviderModule = parent.getModule<MapProviderModule>()
        this.instance = mapProviderModule.instanceContainer.copy().apply {
            chunkLoader = mapProviderModule.instanceContainer.chunkLoader
        }
        MinecraftServer.getInstanceManager().registerInstance(instance)
    }
}