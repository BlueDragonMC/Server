package com.bluedragonmc.server.module.vanilla

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket
import net.minestom.server.tag.Tag

class WeatherModule(private var globalRaining: Boolean = false) : GameModule() {

    private val RAINING = Tag.Boolean("weather:raining").defaultValue(false)
    private lateinit var parent: Game

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.sendPacket(getRainPacket(globalRaining || event.spawnInstance.getTag(RAINING)))
        }
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            event.player.sendPacket(getRainPacket(false))
        }
    }

    fun setRaining(instance: Instance, raining: Boolean) {
        val packet = getRainPacket(raining)
        instance.sendGroupedPacket(packet)
        instance.setTag(RAINING, raining)
    }

    fun setRaining(raining: Boolean) {
        val packet = getRainPacket(raining)
        parent.sendGroupedPacket(packet)
        globalRaining = raining
    }

    private fun getRainPacket(raining: Boolean) = ChangeGameStatePacket(
        if (raining) ChangeGameStatePacket.Reason.BEGIN_RAINING else ChangeGameStatePacket.Reason.END_RAINING,
        0f
    )
}