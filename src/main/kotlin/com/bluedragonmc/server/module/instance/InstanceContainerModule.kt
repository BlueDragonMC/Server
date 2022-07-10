package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance

class InstanceContainerModule : InstanceModule() {
    private lateinit var parent: Game
    override fun getInstance(): Instance = parent.getModule<AnvilFileMapProviderModule>().instanceContainer

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
    }
}