package com.bluedragonmc.server

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.CommonTypes.GameType.GameTypeFieldSelector
import com.bluedragonmc.api.grpc.gameState
import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.model.MapData
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.packet.PerInstanceChatModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.GameState
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
import net.minestom.server.event.EventListener
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.utils.chunk.ChunkUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.function.Consumer
import kotlin.concurrent.timer
import kotlin.reflect.jvm.jvmName

open class Game(val name: String, val mapName: String, val mode: String? = null) : ModuleHolder(),
    PacketGroupingAudience {

    val gameType: CommonTypes.GameType
        get() = gameType {
            name = this@Game.name
            mapName = this@Game.mapName
            if (this@Game.mode != null) {
                mode = this@Game.mode
            }
        }

    val rpcGameState: CommonTypes.GameState
        get() = gameState {
            gameState = state.mapToRpcState()
            openSlots = maxPlayers - players.size
            joinable = state.canPlayersJoin
        }

    internal val players = mutableListOf<Player>()

    var mapData: MapData? = null

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    open val autoRemoveInstance: Boolean = true
    open val preloadSpawnChunks: Boolean = true

    var primaryInstanceId: UUID? = null
        get() = field ?: getInstanceOrNull()?.uniqueId?.also { primaryInstanceId = it }
        private set

    open val maxPlayers = 8

    var state: GameState = GameState.SERVER_STARTING
        set(value) {
            callCancellable(GameStateChangedEvent(this, field, value)) {
                field = value
            }
        }

    private val recentSpawns: Cache<Player, Instance> = Caffeine.newBuilder()
        .weakKeys()
        .weakValues()
        .expireAfterWrite(Duration.ofSeconds(5))
        .build()

    protected val eventNode = EventNode.event("$name-$mapName-$mode", EventFilter.ALL) { event ->
        when (event) {
            is InstanceEvent -> ownsInstance(event.instance)
            is GameEvent -> event.game == this
            is PlayerSpawnEvent -> {
                val primaryInstance = getInstanceOrNull() ?: return@event false
                if (ownsInstance(event.spawnInstance)) {
                    // Prevent `PlayerSpawnEvent`s being called very close to one another for the same instance
                    if (recentSpawns.getIfPresent(event.player) == primaryInstance) {
                        logger.warn("Player ${event.player.username} was already spawned in instance $primaryInstanceId in the last 5 seconds!")
                        return@event false
                    } else {
                        recentSpawns.put(event.player, primaryInstance)
                    }
                    return@event true
                }
                // Workaround for PlayerSpawnEvent not being an InstanceEvent
                return@event false
            }

            is PlayerEvent -> event.player.isActive && ownsInstance(event.player.instance ?: return@event false)
            else -> false
        }
    }

    protected open fun ownsInstance(instance: Instance): Boolean {
        return instance.uniqueId == primaryInstanceId
    }

    override fun <T : GameModule> register(module: T) {
        // Create an event node for the module.
        val eventNode = createEventNode(module)

        module.eventNode = eventNode
        module.initialize(this, eventNode)
    }

    protected open fun useMandatoryModules() {
        use(PerInstanceChatModule)
        Messaging.outgoing.onGameCreated(this)
        handleEvent<PlayerSpawnEvent> {
            playerHasJoined = true
        }
        handleEvent<RemoveEntityFromInstanceEvent> { event ->
            if (event.entity !is Player) return@handleEvent
            callCancellable(PlayerLeaveGameEvent(this, event.entity as Player)) {
                players.remove(event.entity)
            }
        }
    }

    private fun createEventNode(module: GameModule): EventNode<Event> {
        val child = EventNode.all(module::class.simpleName.orEmpty())
        child.priority = module.eventPriority
        eventNode.addChild(child)
        return child
    }

    protected fun onGameStart(handler: Consumer<GameStartEvent>) = handleEvent(handler)

    protected inline fun <reified T : Event> handleEvent(
        handler: Consumer<T>,
    ) = handleEvent(EventListener.of(T::class.java, handler))

    protected inline fun <reified T : Event> handleEvent(handler: EventListener<out T>) {
        eventNode.addListener(handler)
    }

    fun unregister(module: GameModule) {
        logger.debug("Unregistering module $module")
        module.deinitialize()
        modules.remove(module)
        val node = module.eventNode
        node.parent?.removeChild(node)
    }

    private var playerHasJoined = false
    private val creationTime = System.currentTimeMillis()

    private fun shouldRemoveInstance(instance: Instance) =
        autoRemoveInstance && instance.players.isEmpty() &&
                (playerHasJoined || System.currentTimeMillis() - creationTime > 1_800_000L) // 30 minutes

    open fun ready() {
        checkUnmetDependencies()

        logger.debug("Initializing game with modules: ${modules.map { it::class.simpleName ?: it::class.jvmName }}")
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
        timer("instance-unregister-${primaryInstanceId}", daemon = true, period = 10_000) {
            if (shouldRemoveInstance(cachedInstance)) {
                logger.info("Removing instance ${cachedInstance.uniqueId} due to inactivity.")
                removeInstance(cachedInstance)
                this.cancel()
            }
        }

        if (preloadSpawnChunks && !shouldRemoveInstance(cachedInstance)) {
            if (hasModule<SpawnpointModule>()) {
                // If the spawnpoint module is present, preload one chunk at each spawnpoint.
                getModule<SpawnpointModule>().spawnpointProvider.getAllSpawnpoints().forEach { spawnpoint ->
                    cachedInstance.loadOptionalChunk(spawnpoint)
                }
            } else {
                // If not, we can make an educated guess and load the chunks around (0, 0)
                ChunkUtils.forChunksInRange(Pos.ZERO, MinecraftServer.getChunkViewDistance()) { chunkX, chunkZ ->
                    cachedInstance.loadOptionalChunk(chunkX, chunkZ)
                }
            }
        }
    }

    protected open fun removeInstance(instance: Instance) {
        MinecraftServer.getInstanceManager().unregisterInstance(instance)
        AnvilFileMapProviderModule.checkReleaseMap(instance)
        if (getInstanceOrNull() == instance) {
            endGameInstantly(queueAllPlayers = false) // End the game if the game is using the instance which was unregistered
        }
    }

    fun callEvent(event: Event) = eventNode.call(event)
    fun callCancellable(event: Event, successCallback: Runnable) = eventNode.callCancellable(event, successCallback)

    /**
     * Returns the primary instance of the Game. Games may
     * own more than one instance. It is up to each game
     * to decide how to implement multiple instances.
     */
    fun getInstanceOrNull() = getModuleOrNull<InstanceModule>()?.getInstance()
    fun getInstance() = getInstanceOrNull() ?: error("No InstanceModule found.")

    private val isJoinable
        get() = state.canPlayersJoin

    fun addPlayer(player: Player) {
        findGame(player)?.players?.remove(player)
        players.add(player)
        if (player.instance != getInstanceOrNull()) {
            try {
                player.setInstance(getInstance())
            } catch (e: Throwable) {
                e.printStackTrace()
                player.sendMessage(
                    Component.translatable(
                        "queue.error_sending", NamedTextColor.RED,
                        Component.translatable("queue.error.internal_server_error", NamedTextColor.DARK_GRAY)
                    )
                )
                Environment.queue.queue(player, gameType {
                    name = "Lobby"
                    selectors += GameTypeFieldSelector.GAME_NAME
                })
            }
        }
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
        val instanceRef = getInstanceOrNull()
        while (modules.isNotEmpty()) unregister(modules.first())
        if (queueAllPlayers) {
            players.forEach {
                it.sendMessage(Component.translatable("game.status.ending", NamedTextColor.GREEN))
                Environment.queue.queue(it, gameType {
                    name = this@Game.name
                    if (this@Game.mode != null) {
                        mode = this@Game.mode
                        selectors += GameTypeFieldSelector.GAME_MODE
                    }
                    selectors += GameTypeFieldSelector.GAME_NAME
                })
            }
            var task: Task? = null
            var repetitions = 0
            task = MinecraftServer.getSchedulerManager().buildTask {
                repetitions++
                if (instanceRef?.isRegistered == false || instanceRef == null) task!!.cancel()
                getInstanceOrNull()?.players?.forEach { player ->
                    Environment.queue.queue(player, gameType {
                        name = if (repetitions >= 3) "Lobby" else this@Game.name
                        selectors += GameTypeFieldSelector.GAME_NAME
                    })
                    if (repetitions >= 5) {
                        player.kick(Component.text("There was an error adding you to the queue.", NamedTextColor.RED))
                    }
                }
            }.delay(Duration.ofSeconds(10)).repeat(Duration.ofSeconds(10)).schedule()
            players.clear()
        }
    }

    /**
     * Load map data from the database (or from cache)
     */
    protected open fun loadMapData() {
        runBlocking {
            mapData = Database.connection.getMapOrNull(mapName)
            if (mapData == null) logger.warn("No map data found for $mapName!")
        }
    }

    init {
        state = GameState.SERVER_STARTING

        // Initialize mandatory modules for core functionality, like game state updates
        useMandatoryModules()

        loadMapData()

        // Ensure the game was registered with `ready()` method
        MinecraftServer.getSchedulerManager().buildTask {
            if (!games.contains(this) && !playerHasJoined) {
                logger.error("Game was not registered after 25 seconds!")
                endGameInstantly(false)
                games.remove(this)
            }
        }.delay(Duration.ofSeconds(25)).schedule()

        // Allow the game to start receiving events
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
    }

    override fun toString(): String {
        val modules = modules.joinToString { it::class.simpleName ?: it::class.jvmName }
        val players = players.joinToString { it.username }
        return "Game(name='$name', mapName='$mapName', modules=$modules, players=$players, instanceId=$primaryInstanceId, maxPlayers=$maxPlayers, isJoinable=$isJoinable, state=$state)"
    }

    companion object {
        val games = mutableListOf<Game>()

        fun findGame(player: Player): Game? = games.find { player in it.players }
        fun findGame(instanceId: UUID): Game? = games.find { it.primaryInstanceId == instanceId }

        init {
            MinecraftServer.getSchedulerManager().buildShutdownTask {
                ArrayList(games).forEach { game ->
                    game.endGameInstantly(false)
                }
            }
        }
    }
}