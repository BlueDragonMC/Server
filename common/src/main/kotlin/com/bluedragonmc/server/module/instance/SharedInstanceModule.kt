package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.SharedInstance

@DependsOn(AnvilFileMapProviderModule::class)
class SharedInstanceModule : InstanceModule() {

    private lateinit var instanceContainer: InstanceContainer
    private lateinit var instance: SharedInstance

    override fun getSpawningInstance(player: Player): Instance = this.instance
    override fun ownsInstance(instance: Instance): Boolean = instance == this.instance

    fun getInstance() = instance

    override fun getRequiredInstances(): Iterable<Instance> {
        return setOf(instanceContainer)
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        instanceContainer = parent.getModule<AnvilFileMapProviderModule>().instanceContainer
        if (!instanceContainer.isRegistered) {
            MinecraftServer.getInstanceManager().registerInstance(instanceContainer)
        }
        instance = MinecraftServer.getInstanceManager().createSharedInstance(instanceContainer)
    }
}