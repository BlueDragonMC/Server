package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.module.GameModule
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance

abstract class InstanceModule : GameModule() {

    /**
     * Get a set of instances that are required, but not owned, by this module.
     * This is necessary because shared instances must have a registered
     * instance container for chunk loading, but the instance container can be used
     * by multiple games at the same time (and therefore not "owned" by any of them)
     */
    open fun getRequiredInstances(): Iterable<Instance> { return emptySet() }

    /**
     * Get the instance that a player should spawn in when initially joining the game.
     */
    abstract fun getSpawningInstance(player: Player): Instance

    /**
     * Determines whether the module "owns" an instance. Modules should own an instance
     * if they created it, and ownership should be released when the instance is no longer needed.
     * Instances with no modules that declare ownership of them may be cleaned up at any time.
     */
    abstract fun ownsInstance(instance: Instance): Boolean
}