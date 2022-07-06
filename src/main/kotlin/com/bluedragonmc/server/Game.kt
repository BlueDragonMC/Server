package com.bluedragonmc.server

import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.gameplay.SpawnpointModule
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
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.reflect.full.createInstance

open class Game(val name: String) : PacketGroupingAudience {

    internal val modules = mutableListOf<GameModule>()
    internal val players = mutableListOf<Player>()

    private val logger = LoggerFactory.getLogger(this.javaClass)

    val mapName = "Islands" // TODO change this when we get an actual queue system

    init {
        // Initialize mandatory modules
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
        games.add(this)
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
        games.remove(this)
        MinecraftServer.getSchedulerManager().buildTask {
            val instance = getInstance()
            while (modules.isNotEmpty()) unregister(modules.first())
            sendActionBar(Component.text("This game is ending. You will be sent to a new game shortly.", NamedTextColor.GREEN))
            players.forEach {
                queue.queue(it, name)
            }
            MinecraftServer.getSchedulerManager().buildTask {
                MinecraftServer.getInstanceManager().unregisterInstance(instance)
            }.delay(Duration.ofSeconds(30)).schedule() // TODO make this happen automatically when nobody is left in instance

        }.delay(delay).schedule()
    }

    companion object {
        val games = mutableListOf<Game>()

        fun findGame(player: Player): Game? = games.find { player in it.players }
    }
}