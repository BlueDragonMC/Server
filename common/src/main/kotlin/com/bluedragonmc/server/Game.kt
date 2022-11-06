package com.bluedragonmc.server

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.CommonTypes.GameType.GameTypeFieldSelector
import com.bluedragonmc.api.grpc.gameState
import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.MapData
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.packet.PerInstanceChatModule
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

open class Game(val name: String, val mapName: String, val mode: String? = null) : ModuleHolder(), PacketGroupingAudience {

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

    var instanceId: UUID? = null
        get() = field ?: getInstanceOrNull()?.uniqueId?.also { instanceId = it }
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

    override fun <T : GameModule> register(module: T) {
        // Create an event node for the module.
        val eventNode = createEventNode(module)

        module.eventNode = eventNode
        module.initialize(this, eventNode)
    }

    protected open fun useMandatoryModules() {
        use(DatabaseModule())
        use(PerInstanceChatModule)
        use(MessagingModule())
        use(@DependsOn(MessagingModule::class) object : GameModule() {

            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(PlayerSpawnEvent::class.java) {
                    playerHasJoined = true
                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                        getModuleOrNull<MessagingModule>()?.refreshState()
                    }
                }
                eventNode.addListener(PlayerDisconnectEvent::class.java) {
                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                        getModuleOrNull<MessagingModule>()?.refreshState()
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
        child.priority = module.eventPriority
        eventNode.addChild(child)
        return child
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
        autoRemoveInstance && instance.players.isEmpty() && (playerHasJoined || System.currentTimeMillis() - creationTime > 60_000L)

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
        timer("instance-unregister-${instanceId}", daemon = true, period = 10_000) {
            if (shouldRemoveInstance(cachedInstance)) {
                logger.info("Removing instance ${cachedInstance.uniqueId} due to inactivity.")
                MinecraftServer.getInstanceManager().unregisterInstance(cachedInstance)
                AnvilFileMapProviderModule.checkReleaseMap(cachedInstance)
                if (getInstanceOrNull() == cachedInstance) {
                    endGameInstantly(queueAllPlayers = false) // End the game if the game is using the instance which was unregistered
                }
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

    fun callEvent(event: Event) = eventNode.call(event)
    fun callCancellable(event: Event, successCallback: Runnable) = eventNode.callCancellable(event, successCallback)

    fun getInstanceOrNull() = getModuleOrNull<InstanceModule>()?.getInstance()
    fun getInstance() = getInstanceOrNull() ?: error("No InstanceModule found.")

    private val isJoinable
        get() = state.canPlayersJoin

    fun addPlayer(player: Player) {
        findGame(player)?.players?.remove(player)
        players.add(player)
        if (player.instance != getInstanceOrNull()) {
            player.setInstance(getInstance())
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
        while (modules.isNotEmpty()) unregister(modules.first())
        if (queueAllPlayers) {
            players.forEach {
                it.sendMessage(Component.translatable("game.status.ending", NamedTextColor.GREEN))
                Environment.current.queue.queue(it, gameType {
                    name = this@Game.name
                    selectors += GameTypeFieldSelector.GAME_NAME
                })
            }
        }
    }

    /**
     * Load map data from the database (or from cache)
     */
    protected open fun loadMapData() {
        runBlocking {
            mapData = getModule<DatabaseModule>().getMapOrNull(mapName)
            if (mapData == null) logger.warn("No map data found for $mapName!")
        }
    }

    init {

        // Initialize mandatory modules for core functionality, like game state updates
        useMandatoryModules()

        loadMapData()

        // Ensure the game was registered with `ready()` method
        MinecraftServer.getSchedulerManager().buildTask {
            if (!games.contains(this) && !playerHasJoined) {
                logger.error("Game was not registered after 5 seconds!")
                endGameInstantly(false)
                games.remove(this)
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