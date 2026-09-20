package com.bluedragonmc.server

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.gameState
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.game.GameData
import com.bluedragonmc.server.module.GameInfoModule
import com.bluedragonmc.server.module.GameStateModule
import com.bluedragonmc.server.module.PlayerListModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.GameState
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventListener
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.instance.Instance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.random.Random
import kotlin.reflect.jvm.jvmName

abstract class Game(final val data: GameData) : ModuleHolder(), PacketGroupingAudience {

    val rpcGameState: CommonTypes.GameState
        get() = gameState {
            gameState = state.mapToRpcState()
            openSlots = maxPlayers - players.size
            joinable = state.canPlayersJoin
            maxSlots = maxPlayers
        }

    internal val roster = PlayerManager(this)
    val players: List<Player> get() = roster.players

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * A random, 4-character identifier unique to this game.
     */
    val id = (0 until 4).map {
        'a' + Random.Default.nextInt(0, 26)
    }.joinToString("")

    internal val recorder = GameRecorder(this)
    internal val instances = GameInstances(this)
    internal val events = GameEventBus(this)
    internal val lifecycle = GameLifecycle(this)

    override val rootEventNode: EventNode<Event> get() = events.node

    open val maxPlayers = 8

    var state: GameState
        get() = lifecycle.state
        set(value) {
            lifecycle.state = value
        }

    fun ownsInstance(instance: Instance): Boolean {
        return instances.owns(instance)
    }

    protected fun useMandatoryModules() {
        // These provider modules expose the game's capabilities to other modules.
        use(PlayerListModule { roster.players })
        use(GameStateModule(lifecycle))
        use(GameInfoModule(this))

        Messaging.outgoing.onGameCreated(this)
        handleEvent<PlayerDisconnectEvent> { event ->
            roster.remove(event.player)
        }
        onGameStart {
            recorder.recordStart()
        }
        handleEvent<WinModule.WinnerDeclaredEvent> { event ->
            recorder.recordWinner(event)
        }
    }

    protected fun onGameStart(handler: Consumer<GameStartEvent>) = handleEvent(handler)

    protected inline fun <reified T : Event> handleEvent(
        handler: Consumer<T>,
    ) = handleEvent(EventListener.of(T::class.java, handler))

    protected fun <T : Event> handleEvent(handler: EventListener<out T>) {
        use(SingleEventModule(handler))
    }

    fun getOwnedInstances(): List<Instance> = instances.owned()

    fun getRequiredInstances(): List<Instance> = instances.required()

    /**
     * Returns an instance owned by this game.
     * If the game owns multiple instances, an error is thrown.
     */
    fun getInstance() = instances.single()

    private val isJoinable
        get() = state.canPlayersJoin

    fun removeDisconnectedPlayers() = roster.removeDisconnected()

    fun addPlayer(player: Player, sendPlayer: Boolean = true): CompletableFuture<Instance> =
        roster.add(player, sendPlayer)

    override fun getPlayers(): Collection<Player> = roster.players

    fun endGameLater(delay: Duration) = lifecycle.endLater(delay)

    fun endGame(queueAllPlayers: Boolean = true) = lifecycle.end(queueAllPlayers)

    fun isInactive(): Boolean = lifecycle.isInactive()

    fun init() {
        // Initialize mandatory modules for core functionality, like game state updates
        useMandatoryModules()

        // Run the game's initialization code
        initialize()

        // Make sure all module dependencies are resolved
        checkUnmetDependencies()

        logger.debug("Initializing game with modules: {}", modules.map { it::class.simpleName ?: it::class.jvmName })

        // Let the queue system send players to the game
        GameRegistry.add(this)
        state = GameState.WAITING

        // Allow the game to start receiving events
        events.attach()
    }

    protected abstract fun initialize()

    override fun toString(): String {
        val modules = modules.joinToString { it::class.simpleName ?: it::class.jvmName }
        val players = players.joinToString { it.username }
        return "Game(id='$id', data=$data, modules=$modules, players=$players, maxPlayers=$maxPlayers, isJoinable=$isJoinable, state=$state)"
    }
}