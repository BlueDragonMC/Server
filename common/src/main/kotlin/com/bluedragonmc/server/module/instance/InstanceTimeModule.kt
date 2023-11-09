package com.bluedragonmc.server.module.instance

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.config.ConfigModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

/**
 * Uses a config value (if present) to set the time of day/night in the instance
 */
@DependsOn(ConfigModule::class)
class InstanceTimeModule(val default: Int = 12000) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        val time = parent.getModule<ConfigModule>().getConfig().node("world", "time").getInt(default)
        parent.getOwnedInstances().forEach {
            it.time = time.toLong()
            it.timeRate = 0
        }
    }
}
