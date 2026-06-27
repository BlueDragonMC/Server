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
import net.minestom.server.instance.SharedInstance

/**
 * An implementation of [InstanceModule] that create a single [SharedInstance] for each game.
 *
 * [See Documentation](https://developer.bluedragonmc.com/modules/sharedinstancemodule/)
 */
@DependsOn(MapProviderModule::class)
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
        instanceContainer = parent.getModule<MapProviderModule>().instanceContainer
        if (!instanceContainer.isRegistered) {
            MinecraftServer.getInstanceManager().registerInstance(instanceContainer)
        }
        instance = MinecraftServer.getInstanceManager().createSharedInstance(instanceContainer)
    }
}