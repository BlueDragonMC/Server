package com.bluedragonmc.server

import com.bluedragonmc.server.event.GameEvent
import net.minestom.server.MinecraftServer
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.server.ServerTickMonitorEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.event.trait.PlayerEvent
import org.slf4j.LoggerFactory

/**
 * The root event node owned by a single [Game], along with helpers to attach
 * and detach it from the global event handler.
 */
class GameEventBus(private val game: Game) {

    private val logger = LoggerFactory.getLogger(GameEventBus::class.java)

    val node = EventNode.event("${game.id}-${game.data}", EventFilter.ALL) { event ->
        try {
            return@event when (event) {
                is InstanceEvent -> game.ownsInstance(event.instance ?: return@event false)
                is GameEvent -> event.game === game
                is PlayerEvent -> game.players.contains(event.player)
                is ServerTickMonitorEvent -> true
                else -> false
            }
        } catch (e: Exception) {
            logger.error("Error while filtering event $event")
            e.printStackTrace()
            return@event false
        }
    }

    fun attach() {
        MinecraftServer.getGlobalEventHandler().addChild(node)
    }

    fun detach() {
        node.parent?.removeChild(node)
    }
}
