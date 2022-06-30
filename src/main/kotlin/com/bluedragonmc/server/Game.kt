package com.bluedragonmc.server

import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.instance.InstanceModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.trait.InstanceEvent
import java.time.Duration

open class Game(val name: String) : PacketGroupingAudience {

    internal val modules = mutableListOf<GameModule>()
    internal val players = mutableListOf<Player>()

    fun use(module: GameModule) {
        modules.add(module)

        val eventNode = EventNode.event(this.toString(), EventFilter.ALL) { event ->
            when (event) {
                is InstanceEvent -> {
                    hasModule<InstanceModule>() && event.instance == getInstance()
                }
                is GameEvent -> {
                    event.game == this
                }
                is PlayerSpawnEvent -> {
                    // Workaround for PlayerSpawnEvent not being an InstanceEvent
                    hasModule<InstanceModule>() && event.spawnInstance == getInstance()
                }
                else -> false
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        module.eventNode = eventNode
        module.initialize(this, eventNode)
    }

    fun unregister(module: GameModule) {
        module.deinitialize()
        if (module.eventNode != null) {
            val node = module.eventNode!!
            node.parent?.removeChild(node)
        }
        modules.remove(module)
    }

    fun ready() {
        state = GameState.WAITING
    }

    fun callEvent(event: Event) {
        modules.forEach { it.eventNode?.call(event) }
    }

    fun getInstance() = getModule<InstanceModule>().getInstance()

    val isJoinable
        get() = state.canPlayersJoin

    internal inline fun <reified T : GameModule> hasModule(): Boolean = modules.any { it is T }

    internal inline fun <reified T : GameModule> getModule(): T {
        for (module in modules) {
            if (module is T) return module
        }
        error("No module found of type ${T::class.simpleName} on game $this.")
    }

    var state: GameState = GameState.SERVER_STARTING
        protected set

    override fun getPlayers(): MutableCollection<Player> = players

    fun endGame(delay: Duration = Duration.ZERO) {
        MinecraftServer.getSchedulerManager().buildTask {
            while (modules.isNotEmpty()) unregister(modules.first())
            sendMessage(Component.text("This game is ending. You will be sent to a new game shortly.", NamedTextColor.GREEN))
            // TODO queue all players before shutting down the instance
        }.delay(delay).schedule()
    }
}