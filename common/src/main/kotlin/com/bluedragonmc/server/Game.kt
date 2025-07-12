package com.bluedragonmc.server

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.CommonTypes.GameType.GameTypeFieldSelector
import com.bluedragonmc.api.grpc.gameState
import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.event.*
import com.bluedragonmc.server.model.GameDocument
import com.bluedragonmc.server.model.InstanceRecord
import com.bluedragonmc.server.model.PlayerRecord
import com.bluedragonmc.server.model.TeamRecord
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.InstanceUtils
import com.bluedragonmc.server.utils.toPlainText
import kotlinx.coroutines.launch
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
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.server.ServerTickMonitorEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import net.minestom.server.timer.ExecutionType
import net.minestom.server.utils.async.AsyncUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.random.Random
import kotlin.reflect.jvm.jvmName

abstract class Game(val name: String, val mapName: String, val mode: String? = null) : ModuleHolder(),
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
            maxSlots = maxPlayers
        }

    private val _players: MutableList<Player> = CopyOnWriteArrayList()
    val players: List<Player> = _players

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * A random, 4-character identifier unique to this game.
     */
    val id = (0 until 4).map {
        'a' + Random.nextInt(0, 26)
    }.joinToString("")

    private lateinit var startTime: Date
    private lateinit var winningTeam: TeamModule.Team

    open val maxPlayers = 8

    var state: GameState = GameState.SERVER_STARTING
        set(value) {
            callCancellable(GameStateChangedEvent(this, field, value)) {
                field = value
            }
        }

    protected val eventNode = EventNode.event("$name-$mapName-$mode", EventFilter.ALL) { event ->
        try {
            return@event when (event) {
                is InstanceEvent -> ownsInstance(event.instance ?: return@event false)
                is GameEvent -> event.game === this
                is PlayerEvent -> players.contains(event.player)
                is ServerTickMonitorEvent -> true
                else -> false
            }
        } catch (e: Exception) {
            logger.error("Error while filtering event $event")
            e.printStackTrace()
            return@event false
        }
    }

    open fun ownsInstance(instance: Instance): Boolean {
        return getModuleOrNull<InstanceModule>()?.ownsInstance(instance) == true
    }

    override fun <T : GameModule> register(module: T, filter: Predicate<Event>) {
        // Create an event node for the module.
        val eventNode = createEventNode(module, filter)

        module.eventNode = eventNode
        module.initialize(this, eventNode)
    }

    protected open fun useMandatoryModules() {
        Messaging.outgoing.onGameCreated(this)
        handleEvent<PlayerJoinGameEvent> {
            playerHasJoined = true
        }
        handleEvent<PlayerDisconnectEvent> { event ->
            removePlayer(event.player)
        }
        onGameStart {
            startTime = Date()
        }
        handleEvent<WinModule.WinnerDeclaredEvent> { event ->
            winningTeam = event.winningTeam
        }
    }

    private fun createEventNode(module: GameModule, filter: Predicate<Event>): EventNode<Event> {
        val child = EventNode.event(module::class.simpleName.orEmpty(), EventFilter.ALL, filter)
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
        logger.debug("Unregistering module {}", module)
        module.deinitialize()
        modules.remove(module)
        val node = module.eventNode
        node.parent?.removeChild(node)
    }

    private var playerHasJoined = false
    private val creationTime = System.currentTimeMillis()

    open fun getOwnedInstances(): List<Instance> = MinecraftServer.getInstanceManager().instances.filter {
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

    private fun removePlayer(player: Player) {
        _players.remove(player)
        callEvent(PlayerLeaveGameEvent(this, player))
    }

    fun addPlayer(player: Player, sendPlayer: Boolean = true): CompletableFuture<Instance> {
        findGame(player)?.removePlayer(player)
        _players.add(player)
        if (sendPlayer && (player.instance == null || !ownsInstance(player.instance!!))) {
            try {
                return sendPlayerToInstance(player).whenComplete { _, _ -> callEvent(PlayerJoinGameEvent(player, this))}
            } catch (e: Throwable) {
                e.printStackTrace()
                player.sendMessage(
                    Component.translatable(
                        "queue.error_sending", NamedTextColor.RED,
                        Component.translatable("queue.error.internal_server_error", NamedTextColor.DARK_GRAY)
                    )
                )
                Environment.queue.queue(player, gameType {
                    name = Environment.defaultGameName
                    selectors += GameTypeFieldSelector.GAME_NAME
                })
                return AsyncUtils.empty()
            }
        }
        callEvent(PlayerJoinGameEvent(player, this))
        return AsyncUtils.empty()
    }

    open fun sendPlayerToInstance(player: Player): CompletableFuture<Instance> {
        val instance = getModule<InstanceModule>().getSpawningInstance(player)
        if (hasModule<SpawnpointModule>()) {
            val spawnpoint = getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(player)
            return player.setInstance(instance, spawnpoint).thenApply { instance }
        }
        return player.setInstance(instance).thenApply { instance }
    }

    override fun getPlayers(): MutableCollection<Player> = _players

    open fun endGameLater(delay: Duration = Duration.ZERO) {
        state = GameState.ENDING
        games.remove(this)
        MinecraftServer.getSchedulerManager().buildTask {
            endGame()
        }.delay(delay).schedule()
    }

    private fun logGameInfo() {
        if (!::startTime.isInitialized) return

        val statHistory = getModuleOrNull<StatisticsModule>()?.getHistory()
        val teams = getModuleOrNull<TeamModule>()?.teams?.map { team ->
            TeamRecord(
                name = team.name.toPlainText(),
                players = team.players.map { player ->
                    PlayerRecord(
                        uuid = player.uuid,
                        username = player.username
                    )
                }
            )
        }
        val winningTeamRecord = if (::winningTeam.isInitialized) {
            TeamRecord(
                name = winningTeam.name.toPlainText(),
                players = winningTeam.players.map { player ->
                    PlayerRecord(
                        uuid = player.uuid,
                        username = player.username
                    )
                })
        } else null

        val instanceRecords = getOwnedInstances().map { instance ->
            InstanceRecord(
                type = instance::class.jvmName,
                uuid = instance.uuid
            )
        }

        Database.IO.launch {
            Database.connection.logGame(
                GameDocument(
                    gameId = id,
                    serverId = Environment.getServerName(),
                    gameType = name,
                    mapName = mapName,
                    mode = mode,
                    statistics = statHistory,
                    teams = teams,
                    winningTeam = winningTeamRecord,
                    startTime = startTime,
                    endTime = Date(),
                    instances = instanceRecords
                )
            )
        }
    }

    open fun endGame(queueAllPlayers: Boolean = true) {

        logGameInfo()

        state = GameState.ENDING
        games.remove(this)

        val instancesToRemove = MinecraftServer.getInstanceManager().instances.filter { this.ownsInstance(it) }

        // the NotifyInstanceRemovedMessage is published when the MessagingModule is unregistered
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
        }

        MinecraftServer.getSchedulerManager().buildTask {
            instancesToRemove.forEach { instance ->
                if (instance.isRegistered) {
                    logger.info("Forcefully unregistering instance ${instance.uuid}...")
                    InstanceUtils.forceUnregisterInstance(instance)
                }
            }
        }.executionType(ExecutionType.TICK_START).delay(Duration.ofSeconds(10))

        while (players.isNotEmpty()) removePlayer(players.first())
    }

    open fun isInactive(): Boolean {
        // Games with players are always considered active
        if (players.isNotEmpty()) return false
        // Games without players are always inactive after 4 hours
        if (System.currentTimeMillis() - creationTime >= 1_000 * 60 * 60 * 4) return true
        // Games without players are always active in the first 30 minutes after being created
        if (System.currentTimeMillis() - creationTime <= 1_000 * 60 * 30 && !playerHasJoined) return false

        try {
            return runBlocking {
                Messaging.outgoing.checkRemoveInstance(id)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            return true
        }
    }

    fun init() {
        // Initialize mandatory modules for core functionality, like game state updates
        useMandatoryModules()

        // Run the game's initialization code
        initialize()

        // Make sure all module dependencies are resolved
        checkUnmetDependencies()

        logger.debug("Initializing game with modules: {}", modules.map { it::class.simpleName ?: it::class.jvmName })

        // Let the queue system send players to the game
        games.add(this)
        state = GameState.WAITING

        // Allow the game to start receiving events
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
    }

    protected abstract fun initialize()

    override fun toString(): String {
        val modules = modules.joinToString { it::class.simpleName ?: it::class.jvmName }
        val players = players.joinToString { it.username }
        return "Game(id='$id', name='$name', mapName='$mapName', mode='$mode', modules=$modules, players=$players, maxPlayers=$maxPlayers, isJoinable=$isJoinable, state=$state)"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
        val games: MutableList<Game> = CopyOnWriteArrayList()

        /**
         * Instances will be cleaned up every 10 seconds (by default).
         */
        private val INSTANCE_CLEANUP_PERIOD = System.getenv("SERVER_INSTANCE_CLEANUP_PERIOD")?.toLongOrNull() ?: 10_000L

        /**
         * Instances must be inactive for at least 2 minutes
         * to be cleaned up (by default).
         */
        private val CLEANUP_MIN_INACTIVE_TIME =
            System.getenv("SERVER_INSTANCE_MIN_INACTIVE_TIME")?.toLongOrNull() ?: 120_000L

        private val INACTIVE_SINCE_TAG = Tag.Long("instance_inactive_since")


        fun findGame(player: Player): Game? =
            games.find { player in it.players || it.ownsInstance(player.instance ?: return@find false) }

        fun findGame(instanceId: UUID): Game? {
            val instance = MinecraftServer.getInstanceManager().getInstance(instanceId) ?: return null
            return games.find { it.ownsInstance(instance) }
        }

        fun findGame(gameId: String): Game? = games.find { it.id == gameId }

        init {
            MinecraftServer.getSchedulerManager().buildShutdownTask {
                ArrayList(games).forEach { game ->
                    game.endGame(false)
                }
            }

            MinecraftServer.getSchedulerManager().buildTask {
                val instances = MinecraftServer.getInstanceManager().instances

                games.forEach { game ->
                    if (game.isInactive()) {
                        logger.info("Ending inactive game ${game.id} (${game.name}/${game.mapName}/${game.mode})")
                        game.endGame(false)
                    }
                    game._players.removeIf { player -> !player.isOnline }
                }

                instances.forEach { instance ->
                    val owner = games.find { it.ownsInstance(instance) }
                    if (owner != null || games.any { instance in it.getRequiredInstances() }) {
                        return@forEach
                    }
                    if (!instance.hasTag(INACTIVE_SINCE_TAG)) {
                        instance.setTag(INACTIVE_SINCE_TAG, System.currentTimeMillis())
                        return@forEach
                    }
                    if (System.currentTimeMillis() - instance.getTag(INACTIVE_SINCE_TAG) <= CLEANUP_MIN_INACTIVE_TIME) {
                        return@forEach
                    }
                    logger.info("Removing orphan instance ${instance.uuid} (${instance})")
                    InstanceUtils.forceUnregisterInstance(instance)
                }
            }.repeat(Duration.ofMillis(INSTANCE_CLEANUP_PERIOD)).schedule()
        }
    }
}