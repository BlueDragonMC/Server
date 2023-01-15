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
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.packet.PerInstanceChatModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.InstanceUtils
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.adventure.audience.PacketGroupingAudience
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
import net.minestom.server.tag.Tag
import net.minestom.server.timer.ExecutionType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.concurrent.timer
import kotlin.random.Random
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

    /**
     * A random, 4-character identifier unique to this game.
     */
    val id = (0 until 4).map {
        'a' + Random.nextInt(0, 26)
    }.joinToString("")

    open val maxPlayers = 8

    var state: GameState = GameState.SERVER_STARTING
        set(value) {
            callCancellable(GameStateChangedEvent(this, field, value)) {
                field = value
            }
        }

    protected val eventNode = EventNode.event("$name-$mapName-$mode", EventFilter.ALL) { event ->
        when (event) {
            is InstanceEvent -> ownsInstance(event.instance)
            is GameEvent -> event.game === this
            is PlayerSpawnEvent -> ownsInstance(event.spawnInstance) // Workaround for PlayerSpawnEvent not being an InstanceEvent
            is PlayerEvent -> event.player.isActive && ownsInstance(event.player.instance ?: return@event false)
            else -> false
        }
    }

    open fun ownsInstance(instance: Instance): Boolean {
        return getModuleOrNull<InstanceModule>()?.ownsInstance(instance) ?: false
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

    open fun ready() {
        checkUnmetDependencies()

        logger.debug("Initializing game with modules: ${modules.map { it::class.simpleName ?: it::class.jvmName }}")
        logger.debug(dependencyTree.toString())

        // Set time of day according to the MapData
        runBlocking {
            val time = mapData?.time
            if (time != null && time >= 0) {
                getOwnedInstances().forEach { instance ->
                    instance.timeRate = 0
                    instance.time = time.toLong()
                }
            }
        }

        // Let the queue system send players to the game
        games.add(this)
        state = GameState.WAITING
    }

    protected open fun getOwnedInstances(): List<Instance> = MinecraftServer.getInstanceManager().instances.filter {
        ownsInstance(it)
    }

    fun getRequiredInstances(): List<Instance> = getModule<InstanceModule>().getRequiredInstances().toList()

    /**
     * Returns an instance owned by this game.
     * If the game owns multiple instances, an error is thrown.
     */
    fun getInstance() = getOwnedInstances().single()

    fun callEvent(event: Event) = eventNode.call(event)
    fun callCancellable(event: Event, successCallback: Runnable) = eventNode.callCancellable(event, successCallback)

    private val isJoinable
        get() = state.canPlayersJoin

    fun addPlayer(player: Player, sendPlayer: Boolean = true) {
        findGame(player)?.players?.remove(player)
        players.add(player)
        if (sendPlayer && (player.instance == null || !ownsInstance(player.instance!!))) {
            try {
                sendPlayerToInstance(player)
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

    open fun sendPlayerToInstance(player: Player): CompletableFuture<Instance> {
        val instance = getModule<InstanceModule>().getSpawningInstance(player)
        if (hasModule<SpawnpointModule>()) {
            val spawnpoint = getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(player)
            return player.setInstance(instance, spawnpoint).thenApply { instance }
        }
        return player.setInstance(instance).thenApply { instance }
    }

    override fun getPlayers(): MutableCollection<Player> = players

    open fun endGameLater(delay: Duration = Duration.ZERO) {
        state = GameState.ENDING
        games.remove(this)
        MinecraftServer.getSchedulerManager().buildTask {
            endGame()
        }.delay(delay).schedule()
    }

    open fun endGame(queueAllPlayers: Boolean = true) {
        state = GameState.ENDING
        games.remove(this)
        // the NotifyInstanceRemovedMessage is published when the MessagingModule is unregistered
        while (modules.isNotEmpty()) unregister(modules.first())
        modules.forEach { it.deinitialize() }
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
        }
        MinecraftServer.getSchedulerManager().buildTask {
            MinecraftServer.getInstanceManager().instances.filter { this.ownsInstance(it) }.forEach { instance ->
                logger.info("Forcefully unregistering instance ${instance.uniqueId}...")
                InstanceUtils.forceUnregisterInstance(instance).join()
            }
        }.executionType(ExecutionType.ASYNC).delay(Duration.ofSeconds(10))
        players.clear()
    }

    open fun isInactive(): Boolean {
        return players.isEmpty() && (playerHasJoined || System.currentTimeMillis() - creationTime > 600_000)
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
                endGame(false)
                games.remove(this)
            }
        }.delay(Duration.ofSeconds(25)).schedule()

        // Allow the game to start receiving events
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
    }

    override fun toString(): String {
        val modules = modules.joinToString { it::class.simpleName ?: it::class.jvmName }
        val players = players.joinToString { it.username }
        return "Game(id='$id', name='$name', mapName='$mapName', mode='$mode', modules=$modules, players=$players, maxPlayers=$maxPlayers, isJoinable=$isJoinable, state=$state)"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
        val games = mutableListOf<Game>()

        /**
         * Instances will be cleaned up every 5 seconds.
         */
        private const val INSTANCE_CLEANUP_PERIOD = 5_000L

        /**
         * Instances must be inactive for at least 10 seconds
         * to be cleaned up.
         */
        private const val CLEANUP_MIN_INACTIVE_TIME = 10_000L

        fun findGame(player: Player): Game? =
            games.find { player in it.players || it.ownsInstance(player.instance ?: return@find false) }

        fun findGame(instanceId: UUID): Game? {
            val instance = MinecraftServer.getInstanceManager().getInstance(instanceId) ?: return null
            return games.find { it.ownsInstance(instance) }
        }

        fun findGame(gameId: String): Game? = games.find { it.id == gameId }

        private val INACTIVE_SINCE_TAG = Tag.Long("instance_inactive_since")

        init {
            MinecraftServer.getSchedulerManager().buildShutdownTask {
                ArrayList(games).forEach { game ->
                    game.endGame(false)
                }
            }

            timer("Cleanup", daemon = true, period = INSTANCE_CLEANUP_PERIOD) {
                val instances = MinecraftServer.getInstanceManager().instances
                val games = ArrayList(games) // Copy to avoid CME

                instances.forEach { instance ->
                    val owner = findGame(instance.uniqueId)
                    // Remove empty instances which are not owned by a game OR are owned by an inactive game.
                    if ((owner == null || owner.isInactive()) && instance.players.isEmpty()) {
                        // Only instances that are not required by any game should be removed.
                        if (games.none { it.getRequiredInstances().contains(instance) }) {
                            val inactiveSince = instance.getTag(INACTIVE_SINCE_TAG)
                            if (inactiveSince == null) {
                                // The instance has recently turned inactive.
                                instance.setTag(INACTIVE_SINCE_TAG, System.currentTimeMillis())
                            } else {
                                val duration = System.currentTimeMillis() - inactiveSince
                                if (duration >= CLEANUP_MIN_INACTIVE_TIME) {
                                    // Instances inactive for more than the minimum inactive time should be removed.
                                    logger.info("Removing inactive instance ${instance.uniqueId}")
                                    InstanceUtils.forceUnregisterInstance(instance)
                                }
                            }
                        }
                    } else instance.removeTag(INACTIVE_SINCE_TAG)
                }

                // End all inactive games with no owned instances
                games.forEach { game ->
                    if (game.isInactive() && game.getOwnedInstances().isEmpty()) {
                        logger.info("Removing inactive game ${game.id} (${game.name}/${game.mapName}/${game.mode})")
                        game.endGame(false)
                    }
                }
            }
        }
    }
}