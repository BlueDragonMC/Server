package com.bluedragonmc.server.module.map

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.SingleAssignmentProperty
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.InstanceContainer
import java.nio.file.Path

class AnvilFileMapProviderModule(private val worldFolder: Path) : GameModule() {

    var instanceContainer by SingleAssignmentProperty<InstanceContainer>()
        private set

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        if (loadedMaps.containsKey(worldFolder)) {
            instanceContainer = loadedMaps[worldFolder]!!
            return
        }
        instanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer().apply {
            chunkLoader = AnvilLoader(worldFolder)
            loadedMaps[worldFolder] = this
        }
    }

    companion object {
        val loadedMaps = mutableMapOf<Path, InstanceContainer>()
    }
}

