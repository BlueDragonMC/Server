package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer

class InstanceContainerModule : InstanceModule() {

    override val dependencies = listOf(AnvilFileMapProviderModule::class)

    private lateinit var instance: InstanceContainer
    override fun getInstance(): Instance = instance

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        // Create a copy of the loaded InstanceContainer to prevent modifying the state of the original
        this.instance = parent.getModule<AnvilFileMapProviderModule>().instanceContainer.copy().apply {
            chunkLoader = AnvilLoader(parent.getModule<AnvilFileMapProviderModule>().worldFolder)
        }
        MinecraftServer.getInstanceManager().registerInstance(instance)
    }
}