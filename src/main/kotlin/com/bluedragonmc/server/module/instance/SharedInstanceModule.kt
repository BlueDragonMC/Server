package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance
import net.minestom.server.instance.SharedInstance

class SharedInstanceModule : InstanceModule() {

    private lateinit var instance: SharedInstance

    override fun getInstance(): Instance = instance

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        val instanceContainer = parent.getModule<AnvilFileMapProviderModule>().instanceContainer
        instance = MinecraftServer.getInstanceManager().createSharedInstance(instanceContainer)
    }
}