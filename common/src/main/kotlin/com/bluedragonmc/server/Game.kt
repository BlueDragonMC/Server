package com.bluedragonmc.server

import com.bluedragonmc.messages.GameStateUpdateMessage
import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.MapData
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.packet.PerInstanceChatModule
import com.bluedragonmc.server.utils.*
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.instance.Instance
import net.minestom.server.utils.chunk.ChunkUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.concurrent.timer
import kotlin.reflect.jvm.jvmName

open class Game(val name: String, val mapName: String, val mode: String? = null) : PacketGroupingAudience {

    internal val players = mutableListOf<Player>()

    var mapData: MapData? = null

    protected val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    open val autoRemoveInstance: Boolean = true
    open val preloadSpawnChunks: Boolean = true

    /**
     * A list of modules that have been added with the [use] method, but their dependencies have not been added.
     */
    private val dependencyTree = Root<ModuleDependency<*>>()

    /**
     * A list of modules that have been loaded and subscribed to an event node.
     */
    val modules = mutableListOf<GameModule>()

    var instanceId: UUID? = null
        get() = field ?: getInstanceOrNull()?.uniqueId?.also { instanceId = it }
        private set

    open val maxPlayers = 8

    var state: GameState = GameState.SERVER_STARTING
        set(value) {
            field = value
            MessagingModule.publish(getGameStateUpdateMessage())
        }

    private val recentSpawns: Cache<Player, Instance> = Caffeine.newBuilder()
        .weakKeys()
        .weakValues()
        .expireAfterWrite(Duration.ofSeconds(5))
        .build()

    private val eventNode = EventNode.event("$name-$mapName-$mode", EventFilter.ALL) { event ->
        when (event) {
            is DataLoadedEvent -> true
            is InstanceEvent -> event.instance == getInstanceOrNull()
            is GameEvent -> event.game == this
            is PlayerSpawnEvent -> {
                val instance = getInstanceOrNull() ?: return@event false
                if (event.spawnInstance == instance) {
                    // Prevent `PlayerSpawnEvent`s being called very close to one another for the same instance
                    if (recentSpawns.getIfPresent(event.player) == instance) {
                        logger.warn("Player ${event.player.username} was already spawned in instance ${instance.uniqueId} in the last 5 seconds!")
                        return@event false
                    } else {
                        recentSpawns.put(event.player, event.spawnInstance)
                    }
                    return@event true
                }
                // Workaround for PlayerSpawnEvent not being an InstanceEvent
                return@event false
            }
            is PlayerEvent -> event.player.instance == getInstanceOrNull() && event.player.isActive
            else -> false
        }
    }

    fun <T : GameModule> use(module: T): T {
        logger.debug("Attempting to register module $module")
        if (modules.any { it == module }) throw IllegalStateException("Tried to register module that is already registered: $module")

        // Create a node in the dependency tree for this module
        val moduleDependencyNode = Node<ModuleDependency<*>>(FilledModuleDependency(module::class, module))
        // Add this module's dependencies as children in its branch of the tree,
        // and satisfy all dependencies which have a module that's already registered of the required type.
        moduleDependencyNode.addChildren(module.dependencies.map {
            modules.firstOrNull { module -> it.isInstance(module) }?.let { found ->
                return@map FilledModuleDependency(found::class, found)
            }
            EmptyModuleDependency(it)
        })
        dependencyTree.addChild(moduleDependencyNode)
        logger.trace("Added node to dependency tree: node: $moduleDependencyNode")
        // If not all the module's dependencies were found, delay the loading of
        // the module until after all of its dependencies have been registered.
        if (!module.dependencies.all { dep ->
                modules.any { module -> dep.isInstance(module) }
            }) {
            logger.debug("Waiting for dependencies of module $module to load before registering.")
            return module
        }

        // At this point, the module can be registered.

        // Create an event node for the module.
        val eventNode = createEventNode(module)

        module.eventNode = eventNode
        module.initialize(this, eventNode)
        modules.add(module)

        val depth = dependencyTree.maxDepth()
        if (depth == 0) return module
        val entries = dependencyTree.elementsAtDepth(depth)
        entries.forEach { node ->
            if (node.value is EmptyModuleDependency && node.value!!.type.isInstance(module)) {
                logger.debug("Dependency [${node.value!!.type}] of module ${node.parent.value} SOLVED with $module")
                node.value = FilledModuleDependency(node.value!!.type, module)
                // If all dependencies have been filled, use this module.
                if (node.parent.value is FilledModuleDependency) { // This will never be false at a tree depth <2 because the nodes at depth=1 are always instances of FilledModuleDependency
                    logger.trace("Sibling module dependencies of ${node.value?.toString()}: ${
                        node.getSiblings().map { it.value?.toString() }
                    }")
                    if (node.getSiblings().none { sibling -> sibling.value is EmptyModuleDependency<*> }) {
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

    private fun createEventNode(module: GameModule): EventNode<Event> {
        val child = EventNode.all(module::class.simpleName.orEmpty())
        child.setPriority(module.eventPriority)
        eventNode.addChild(child)
        return child
    }

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
    private val creationTime = System.currentTimeMillis()

    private fun shouldRemoveInstance(instance: Instance) =
        autoRemoveInstance && instance.players.isEmpty() && (playerHasJoined || System.currentTimeMillis() - creationTime > 60_000L)

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
            if (hasModule<SpawnpointModule>()) {
                // If the spawnpoint module is present, preload the chunks around each spawnpoint.
                getModule<SpawnpointModule>().spawnpointProvider.getAllSpawnpoints().forEach { spawnpoint ->
                    ChunkUtils.forChunksInRange(spawnpoint, MinecraftServer.getChunkViewDistance()) { chunkX, chunkZ ->
                        cachedInstance.loadOptionalChunk(chunkX, chunkZ)
                    }
                }
            } else {
                // If not, we can make an educated guess and load the chunks around (0, 0)
                ChunkUtils.forChunksInRange(Pos.ZERO, MinecraftServer.getChunkViewDistance()) { chunkX, chunkZ ->
                    cachedInstance.loadOptionalChunk(chunkX, chunkZ)
                }
            }
        }
    }

    fun callEvent(event: Event) {
        eventNode.call(event)
    }

    fun callCancellable(event: Event, successCallback: () -> Unit) {
        eventNode.callCancellable(event, successCallback)
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
        if (player.instance != getInstanceOrNull()) {
            player.setInstance(getInstance())
        }
    }

    inline fun <reified T : GameModule> hasModule(): Boolean = modules.any { it is T }

    inline fun <reified T : GameModule> getModule(): T {
        return getModuleOrNull() ?: error("No module found of type ${T::class.simpleName} on game $this.")
    }

    inline fun <reified T : GameModule> getModuleOrNull(): T? {
        for (module in modules) {
            if (module is T) return module
        }
        return null
    }

    override fun getPlayers(): MutableCollection<Player> = players

    fun endGame(delay: Duration = Duration.ZERO) {
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

        // Allow the game to start receiving events
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
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