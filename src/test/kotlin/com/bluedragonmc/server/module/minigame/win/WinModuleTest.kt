package com.bluedragonmc.server.module.minigame.win

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.ModuleHolder
import com.bluedragonmc.server.api.DatabaseConnectionStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.game.GameData
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Maps
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.GameState
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.Instance
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.CachedPacket
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import net.minestom.testing.Env
import net.minestom.testing.EnvTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the standings and tie handling of [WinModule].
 */
@EnvTest
class WinModuleTest {

    private val nameCounter = AtomicInteger()

    private class TestInstanceModule(private val env: Env) : InstanceModule() {
        lateinit var created: Instance

        override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {
            created = env.createFlatInstance()
        }

        override fun getSpawningInstance(player: Player): Instance = created
        override fun ownsInstance(instance: Instance): Boolean = instance == created
    }

    private class TestGame(private val env: Env, val winModule: WinModule) : Game(
        GameData("test", Maps.MapSource("test", "test", CommonTypes.MapFormat.UNRECOGNIZED, ""), null)
    ) {
        lateinit var createdInstance: Instance

        override fun initialize() {
            val instanceModule = TestInstanceModule(env)
            use(instanceModule)
            createdInstance = instanceModule.created
            use(SpawnpointModule(SpawnpointModule.TestSpawnpointProvider(Pos(0.0, 42.0, 0.0))))
            use(winModule)
        }
    }

    private class RecordingConnection : PlayerConnection() {
        private var online = true

        override fun sendPacket(packet: SendablePacket) {
            // Resolve cached packets so that no packet is left unhandled.
            if (packet is CachedPacket) packet.packet(serverState)
        }

        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("localhost", 25565)
        override fun isOnline(): Boolean = online
        override fun disconnect() {
            online = false
        }
    }

    @BeforeEach
    fun setup() {
        Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
        Database.initialize(DatabaseConnectionStub())
    }

    private fun connect(env: Env, instance: Instance): Player {
        val profile = GameProfile(UUID.randomUUID(), "Player${nameCounter.incrementAndGet()}")
        val connection = RecordingConnection()
        val player = env.process().connection().createPlayer(connection, profile)
        player.eventNode().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = instance
        }

        val future = CompletableFuture<Player>()
        Thread.startVirtualThread {
            env.process().connection().doConfiguration(player, false)
            env.process().connection().transitionConfigToPlay(player)
            future.complete(player)
        }
        future.join()
        connection.setClientState(ConnectionState.PLAY)
        connection.setServerState(ConnectionState.PLAY)
        env.process().connection().updateWaitingPlayers()
        return player
    }

    @Test
    fun `Scores are ranked descending with ties grouped`(env: Env) {
        val game = TestGame(env, WinModule(WinModule.WinCondition.MANUAL, ranking = Ranking.SCORE_DESC))
        game.init()
        val a = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }
        val b = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }
        val c = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }

        game.winModule.reportScore(a, 60.0)
        game.winModule.reportScore(b, 60.0)
        game.winModule.reportScore(c, 24.0)

        val standings = game.winModule.getStandings()
        assertEquals(2, standings.size)
        assertEquals(listOf(1, 3), standings.map { it.rank })
        assertEquals(2, standings[0].competitors.size)
        assertTrue(standings[0].competitors.contains(Competitor.of(a)))
        assertTrue(standings[0].competitors.contains(Competitor.of(b)))
        assertEquals(60.0, standings[0].score)
        assertEquals(listOf(Competitor.of(c)), standings[1].competitors)
    }

    @Test
    fun `Score threshold ties multiple competitors`(env: Env) {
        val game = TestGame(
            env,
            WinModule(
                WinModule.WinCondition.MANUAL,
                ranking = Ranking.SCORE_DESC,
                scoreThreshold = 60.0,
                tieWindow = Duration.ofMillis(50),
            )
        )
        game.init()
        val a = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }
        val b = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }

        game.winModule.reportScore(a, 60.0)
        game.winModule.reportScore(b, 61.0)

        Thread.sleep(100)
        // Let the scheduled threshold check run
        repeat(5) { env.tick() }

        assertEquals(GameState.ENDING, game.state)
    }

    @Test
    fun `Survival ranking puts survivors first and groups near-simultaneous eliminations`(env: Env) {
        val game = TestGame(env, WinModule(WinModule.WinCondition.LAST_PLAYER_ALIVE, tieWindow = Duration.ofMillis(50)))
        game.init()
        val a = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }
        val b = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }
        val c = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }
        val d = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }

        // c and d are eliminated first (near each other in time); b is eliminated later
        game.winModule.reportElimination(c)
        game.winModule.reportElimination(d)
        Thread.sleep(100)
        game.winModule.reportElimination(b)

        val standings = game.winModule.getStandings()
        // a survives (rank 1), b is second, c and d are tied last
        assertEquals(listOf(1, 2, 3), standings.map { it.rank })
        assertEquals(listOf(Competitor.of(a)), standings[0].competitors)
        assertEquals(listOf(Competitor.of(b)), standings[1].competitors)
        assertEquals(2, standings[2].competitors.size)
    }

    @Test
    fun `Win is declared with tie via declareWinners`(env: Env) {
        val game = TestGame(env, WinModule(WinModule.WinCondition.MANUAL))
        game.init()
        val a = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }
        val b = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }

        var winningPlayers: Collection<Player>? = null
        game.rootEventNode.addListener(WinModule.WinnerDeclaredEvent::class.java) { event ->
            winningPlayers = event.winningTeamPlayers
        }

        game.winModule.declareWinners(listOf(Competitor.of(a), Competitor.of(b)))

        assertEquals(setOf(a, b), winningPlayers?.toSet())
        assertEquals(GameState.ENDING, game.state)
    }

    @Test
    fun `clearStandings suppresses the top three`(env: Env) {
        val game = TestGame(env, WinModule(WinModule.WinCondition.MANUAL, ranking = Ranking.SCORE_DESC))
        game.init()
        val a = connect(env, game.createdInstance).also { game.addPlayer(it, sendPlayer = false) }
        game.winModule.reportScore(a, 10.0)
        assertEquals(1, game.winModule.getStandings().size)

        game.winModule.clearStandings()
        assertTrue(game.winModule.getStandings().isEmpty())
    }
}
