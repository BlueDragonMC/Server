package com.bluedragonmc.server

import com.bluedragonmc.messages.GameStateUpdateMessage
import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.messaging.MessagingModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerPacketOutEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.trait.CancellableEvent
import net.minestom.server.event.trait.InstanceEvent
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID

open class Game(val name: String, val mapName: String) : PacketGroupingAudience {

    internal val modules = mutableListOf<GameModule>()
    internal val players = mutableListOf<Player>()

    private val logger = LoggerFactory.getLogger(this.javaClass)

    init {

        // Initialize mandatory modules with no requirements
        use(DatabaseModule())

        // Ensure the game was registered with `ready()` method
        MinecraftServer.getSchedulerManager().buildTask {
            if(!games.contains(this)) {
                logger.warn("Game was not registered after 5 seconds! Games MUST call the ready() method after they are constructed or they will not be joinable.")
            }
        }.delay(Duration.ofSeconds(5)).schedule()
    }

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
                is PlayerPacketOutEvent -> {
                    hasModule<InstanceModule>() && event.player.instance == getInstance()
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
        // Initialize mandatory modules which require an InstanceModule
        use(MessagingModule())
        use(object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(PlayerSpawnEvent::class.java) {
                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                        MessagingModule.publish(getGameStateUpdateMessage())
                    }
                }
                eventNode.addListener(PlayerDisconnectEvent::class.java) {
                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                        MessagingModule.publish(getGameStateUpdateMessage())
                    }
                }
            }
        })

        games.add(this)
        state = GameState.WAITING
    }

    fun callEvent(event: Event) {
        modules.forEach { it.eventNode?.call(event) }
    }

    fun callCancellable(event: Event, successCallback: () -> Unit) {
        modules.forEach {
            it.eventNode?.call(event)
        }
        if (event is CancellableEvent && !event.isCancelled) successCallback()
    }

    fun getInstance() = getModule<InstanceModule>().getInstance()
    private val instanceId by lazy {
        getInstance().uniqueId
    }
    open val maxPlayers = 8

    fun getGameStateUpdateMessage() = GameStateUpdateMessage(instanceId, if(state.canPlayersJoin) maxPlayers - players.size else 0)

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
        set(value) {
            field = value
            MessagingModule.publish(getGameStateUpdateMessage())
        }

    override fun getPlayers(): MutableCollection<Player> = players

    fun endGame(delay: Duration = Duration.ZERO) {
        state = GameState.ENDING
        games.remove(this)
        MinecraftServer.getSchedulerManager().buildTask {
            endGameInstantly()
        }.delay(delay).schedule()
    }

    private fun endGameInstantly() {
        val instance = getInstance()
        while (modules.isNotEmpty()) unregister(modules.first())
        sendActionBar(Component.text("This game is ending. You will be sent to a new game shortly.", NamedTextColor.GREEN))
        players.forEach {
            queue.queue(it, GameType(name, null, null))
        }
        MinecraftServer.getSchedulerManager().buildTask {
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
        }.delay(Duration.ofSeconds(30)).schedule() // TODO make this happen automatically when nobody is left in instance
    }

    companion object {
        val games = mutableListOf<Game>()

        fun findGame(player: Player): Game? = games.find { player in it.players }
        fun findGame(instanceId: UUID): Game? = games.find { it.instanceId == instanceId }

        init {
            MinecraftServer.getSchedulerManager().buildShutdownTask {
                games.forEach { game ->
                    game.endGameInstantly()
                }
            }
        }
    }
}