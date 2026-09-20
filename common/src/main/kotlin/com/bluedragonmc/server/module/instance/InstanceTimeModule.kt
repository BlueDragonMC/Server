package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.*
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.config.ConfigModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

/**
 * Uses a config value (if present) to set the time of day/night in the instance
 *
 * [See Documentation](https://developer.bluedragonmc.com/modules/instancetimemodule/)
 */
@DependsOn(ConfigModule::class, InstanceModule::class)
class InstanceTimeModule(val default: Int = 12000) : GameModule() {
    override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {
        val time = getModule<ConfigModule>().getConfig().node("world", "time").getInt(default)
        getModule<InstanceModule>().getOwnedInstances().forEach {
            it.time = time.toLong()
            it.defaultClock()?.pause()
        }
    }
}
