package com.bluedragonmc.server

import com.bluedragonmc.server.module.instance.InstanceModule
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance

/**
 * Keeps track of the [Instance]s owned or required by a single [Game].
 */
class GameInstances(private val game: Game) {

    /**
     * Returns whether [instance] is owned by this game.
     */
    fun owns(instance: Instance): Boolean {
        return game.getModuleOrNull<InstanceModule>()?.ownsInstance(instance) == true
    }

    /**
     * Returns every instance owned by this game.
     */
    fun owned(): List<Instance> = MinecraftServer.getInstanceManager().instances.filter { owns(it) }

    /**
     * Returns every instance required by this game's [InstanceModule].
     */
    fun required(): List<Instance> = game.getModule<InstanceModule>().getRequiredInstances().toList()

    /**
     * Returns an instance owned by this game.
     * If the game owns multiple instances, an error is thrown.
     */
    fun single(): Instance = owned().single()
}
