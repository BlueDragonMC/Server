package com.bluedragonmc.server

import com.bluedragonmc.messages.GameStateUpdateMessage
import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.MapData
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.module.packet.PerInstanceChatModule
import com.bluedragonmc.server.utils.*
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.coordinate.Pos
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
import net.minestom.server.instance.Instance
import net.minestom.server.utils.chunk.ChunkUtils
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.concurrent.timer
import kotlin.reflect.jvm.jvmName

open class Game(val name: String, val mapName: String, val mode: String? = null) : PacketGroupingAudience {

    internal val players = mutableListOf<Player>()

    internal var mapData: MapData? = null

    protected val logger = LoggerFactory.getLogger(this.javaClass)

    open val autoRemoveInstance: Boolean = true
    open val preloadSpawnChunks: Boolean = true

    /**
     * A list of modules that have been added with the [use] method, but their dependencies have not been added.
     */
    private val dependencyTree = Root<ModuleDependency<*>>()

    /**
     * A list of modules that have been loaded and subscribed to an event node.
     */
    internal val modules = mutableListOf<GameModule>()

    var instanceId: UUID? = null
        get() = field ?: getInstanceOrNull()?.uniqueId?.also { instanceId = it }
        private set

    open val maxPlayers = 8

    var state: GameState = GameState.SERVER_STARTING
        set(value) {
            field = value
            MessagingModule.publish(getGameStateUpdateMessage())
        }

    init {

        // Initialize mandatory modules for core functionality, like game state updates
        useMandatoryModules()

        // Load map data from the database (or from cache)
        runBlocking {
            mapData = getModule<DatabaseModule>().getMapOrNull(mapName)
            if (mapData == null) logger.warn("No map data found for $mapName!")
        }

        // Ensure the game was registered with `ready()` method
        MinecraftServer.getSchedulerManager().buildTask {
            if (!games.contains(this)) {
                logger.warn("Game was not registered after 5 seconds! Games MUST call the ready() method after they are constructed or they will not be joinable.")
            }
        }.delay(Duration.ofSeconds(5)).schedule()
    }

    fun <T: GameModule> use(module: T): T {

        if (modules.any { it == module }) throw IllegalStateException("Tried to register module that is already registered: $module")

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
            return module
        }

        // Create an event node for the module.
        val eventNode = createEventNode(module)

        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        module.eventNode = eventNode
        module.initialize(this, eventNode)
        modules.add(module)

        val depth = dependencyTree.maxDepth()
        if (depth == 0) return module
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
        return module
    }

    private fun useMandatoryModules() {
        use(DatabaseModule())
        use(PerInstanceChatModule)
        use(MessagingModule())
        use(object : GameModule() {
            override val dependencies = listOf(MessagingModule::class)

            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(PlayerSpawnEvent::class.java) {
                    playerHasJoined = true
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
                    if (event.entity !is Player) return@addListener
                    callEvent(PlayerLeaveGameEvent(parent, event.entity as Player))
                    players.remove(event.entity)
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

    private var playerHasJoined = false
    private fun shouldRemoveInstance(instance: Instance) =
        autoRemoveInstance && instance.players.isEmpty() && playerHasJoined

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

        // Set time of day according to the MapData
        runBlocking {
            val time = mapData?.time
            if (time != null && time >= 0) {
                val instance = getInstance()
                instance.timeRate = 0
                instance.time = time.toLong()
            }
        }

        // Let the queue system send players to the game
        games.add(this)
        state = GameState.WAITING

        val cachedInstance = getInstance()
        timer("instance-unregister-${instanceId}", daemon = true, period = 10_000) {
            if (shouldRemoveInstance(cachedInstance)) {
                logger.info("Removing instance ${cachedInstance.uniqueId} due to inactivity.")
                MinecraftServer.getInstanceManager().unregisterInstance(cachedInstance)
                AnvilFileMapProviderModule.checkReleaseMap(cachedInstance)
                endGameInstantly(queueAllPlayers = false)
                this.cancel()
            }
        }

        if (preloadSpawnChunks && !shouldRemoveInstance(cachedInstance)) {
            ChunkUtils.forChunksInRange(Pos.ZERO, MinecraftServer.getChunkViewDistance()) { chunkX, chunkZ ->
                cachedInstance.loadOptionalChunk(chunkX, chunkZ)
            }
        }
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

    override fun getPlayers(): MutableCollection<Player> = players

    internal fun endGame(delay: Duration = Duration.ZERO) {
        state = GameState.ENDING
        games.remove(this)
        MinecraftServer.getSchedulerManager().buildTask {
            endGameInstantly()
        }.delay(delay).schedule()
    }

    protected fun endGameInstantly(queueAllPlayers: Boolean = true) {
        state = GameState.ENDING
        games.remove(this)
        // the NotifyInstanceRemovedMessage is published when the MessagingModule is unregistered
        while (modules.isNotEmpty()) unregister(modules.first())
        if (queueAllPlayers) {
            players.forEach {
                it.sendMessage(Component.translatable("game.status.ending", NamedTextColor.GREEN))
                Environment.current.queue.queue(it, GameType(name, null, null))
            }
        }
    }

    override fun toString(): String {
        val modules = modules.joinToString { it::class.simpleName ?: it::class.jvmName }
        val players = players.joinToString { it.username }
        return "Game(name='$name', mapName='$mapName', modules=$modules, players=$players, instanceId=$instanceId, maxPlayers=$maxPlayers, isJoinable=$isJoinable, state=$state)"
    }

    companion object {
        val games = mutableListOf<Game>()

        fun findGame(player: Player): Game? = games.find { player in it.players }
        fun findGame(instanceId: UUID): Game? = games.find { it.instanceId == instanceId }

        init {
            MinecraftServer.getSchedulerManager().buildShutdownTask {
                ArrayList(games).forEach { game ->
                    game.endGameInstantly(false)
                }
            }
        }
    }
}