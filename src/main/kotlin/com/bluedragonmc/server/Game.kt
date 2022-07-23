package com.bluedragonmc.server

import com.bluedragonmc.messages.GameStateUpdateMessage
import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.module.packet.PerInstanceChatModule
import com.bluedragonmc.server.utils.Node
import com.bluedragonmc.server.utils.Root
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.trait.CancellableEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.event.trait.PlayerEvent
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.reflect.KClass

open class Game(val name: String, val mapName: String) : PacketGroupingAudience {

    internal val players = mutableListOf<Player>()

    private val logger = LoggerFactory.getLogger(this.javaClass)

    /**
     * A list of modules that have been added with the [use] method, but their dependencies have not been added.
     */
    private val dependencyTree = Root<ModuleDependency<*>>()

    /**
     * A list of modules that have been loaded and subscribed to an event node.
     */
    internal val modules = mutableListOf<GameModule>()

    interface ModuleDependency<T : GameModule> {
        val type: KClass<T>
    }

    data class FilledModuleDependency<T : GameModule>(override val type: KClass<T>, val instance: GameModule) :
        ModuleDependency<T> {
        override fun toString(): String {
            instance::class.simpleName?.let { className ->
                return className + "@" + instance.hashCode()
            }
            return instance.toString()
        }
    }

    data class EmptyModuleDependency<T : GameModule>(override val type: KClass<T>) : ModuleDependency<T> {
        override fun toString(): String = type.simpleName ?: type.toString()
    }

    init {

        // Initialize mandatory modules with no requirements
        useMandatoryModules()

        // Ensure the game was registered with `ready()` method
        MinecraftServer.getSchedulerManager().buildTask {
            if (!games.contains(this)) {
                logger.warn("Game was not registered after 5 seconds! Games MUST call the ready() method after they are constructed or they will not be joinable.")
            }
        }.delay(Duration.ofSeconds(5)).schedule()
    }

    fun use(module: GameModule) {

        if(modules.any { it == module }) throw IllegalStateException("Tried to register module that is already registered: $module")

        val moduleDependencyNode = Node<ModuleDependency<*>>(FilledModuleDependency(module::class, module))
        moduleDependencyNode.addChildren(module.dependencies.map {
            modules.firstOrNull { module -> it.isInstance(module) }?.let { found ->
                return@map FilledModuleDependency(found::class, found)
            }
            EmptyModuleDependency(it)
        })
        dependencyTree.addChild(moduleDependencyNode)
        if (!module.dependencies.all { dep ->
                modules.any { module -> dep.isInstance(module) }
            }) {
            logger.debug("Waiting for dependencies of module $module to load before registering.")
            return
        }

        // Create an event node for the module.
        val eventNode = createEventNode(module)

        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        module.eventNode = eventNode
        module.initialize(this, eventNode)
        modules.add(module)

        val depth = dependencyTree.maxDepth()
        if (depth == 0) return
        val entries = dependencyTree.elementsAtDepth(depth)
        entries.forEach { node ->
            if (node.value is EmptyModuleDependency && node.value!!.type.isInstance(module)) {
                logger.debug("Dependency of module ${node.value} SOLVED with $module")
                node.value = FilledModuleDependency(node.value!!.type, module)
                // If all dependencies have been filled, use this module.
                if (node.parent.value is FilledModuleDependency) { // This will never be false at a tree depth <2 because the nodes at depth=1 are always instances of FilledModuleDependency
                    if (node.parent.getChildren().filterIsInstance<EmptyModuleDependency<*>>().isEmpty()) {
                        val parentModule = (node.parent.value as FilledModuleDependency<*>).instance
                        logger.debug("Using module because its dependencies have been solved: $parentModule")
                        use(parentModule)
                    }
                }
            }
        }
    }

    private fun useMandatoryModules() {
        use(DatabaseModule())
        use(PerInstanceChatModule)
        use(MessagingModule())
        use(object : GameModule() {
            override val dependencies = listOf(MessagingModule::class)

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
                eventNode.addListener(RemoveEntityFromInstanceEvent::class.java) { event ->
                    if (event.entity is Player) {
                        callEvent(PlayerLeaveGameEvent(parent, event.entity as Player))
                        players.remove(event.entity)
                    }
                }
            }
        })
    }

    private fun createEventNode(module: GameModule) =
        EventNode.event(module::class.simpleName.orEmpty(), EventFilter.ALL) { event ->
            when (event) {
                is InstanceEvent -> event.instance == getInstanceOrNull()
                is GameEvent -> event.game == this
                is PlayerSpawnEvent -> event.spawnInstance == getInstanceOrNull() // Workaround for PlayerSpawnEvent not being an InstanceEvent
                is PlayerEvent -> event.player.instance == getInstanceOrNull()
                else -> false
            }
        }.apply { priority = module.eventPriority }

    fun unregister(module: GameModule) {
        logger.debug("Unregistering module $module")
        module.deinitialize()
        modules.remove(module)
        if (module.eventNode != null) {
            val node = module.eventNode!!
            node.parent?.removeChild(node)
        }
    }

    fun ready() {
        val modules = dependencyTree.elementsAtDepth(1)
        val unfilledDependencies = modules.filter { it.value !is FilledModuleDependency<*> }
        if (unfilledDependencies.isNotEmpty()) {
            for (dep in unfilledDependencies) {
                throw IllegalStateException("Game has unfilled module dependencies: Module '${dep.parent.value?.type}' requires a module of type '${dep.value?.type}', but none were found.")
            }
        }
        logger.debug("Initializing game with modules: ${modules.map { it.value?.type?.simpleName ?: "<Anonymous module>" }}")
        logger.debug(dependencyTree.toString())
        games.add(this)
        state = GameState.WAITING
    }

    fun callEvent(event: Event) {
        ArrayList(modules).forEach { it.eventNode?.call(event) }
    }

    fun callCancellable(event: Event, successCallback: () -> Unit) {
        ArrayList(modules).forEach {
            it.eventNode?.call(event)
        }
        if (event is CancellableEvent && !event.isCancelled) successCallback()
    }

    fun getInstanceOrNull() = getModuleOrNull<InstanceModule>()?.getInstance()
    fun getInstance() = getInstanceOrNull() ?: error("No InstanceModule found.")

    private var instanceId: UUID? = null
        get() = field ?: getInstanceOrNull()?.uniqueId?.also { instanceId = it }

    open val maxPlayers = 8

    fun getGameStateUpdateMessage() =
        GameStateUpdateMessage(instanceId!!, if (isJoinable) maxPlayers - players.size else 0)

    private val isJoinable
        get() = state.canPlayersJoin

    fun addPlayer(player: Player) {
        findGame(player)?.players?.remove(player)
        players.add(player)
        player.setInstance(getInstance())
    }

    internal inline fun <reified T : GameModule> hasModule(): Boolean = modules.any { it is T }

    internal inline fun <reified T : GameModule> getModule(): T {
        return getModuleOrNull() ?: error("No module found of type ${T::class.simpleName} on game $this.")
    }

    internal inline fun <reified T : GameModule> getModuleOrNull(): T? {
        for (module in modules) {
            if (module is T) return module
        }
        return null
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
        }.delay(Duration.ofSeconds(30))
            .schedule() // TODO make this happen automatically when nobody is left in instance
    }

    override fun toString(): String {
        val modules = modules.joinToString { it::class.simpleName ?: "<Anonymous module>" }
        val players = players.joinToString { it.username }
        return "Game(name='$name', mapName='$mapName', modules=$modules, players=$players, instanceId=$instanceId, maxPlayers=$maxPlayers, isJoinable=$isJoinable, state=$state)"
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