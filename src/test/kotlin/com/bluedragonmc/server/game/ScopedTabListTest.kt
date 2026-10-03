package com.bluedragonmc.server.game

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.ModuleHolder
import com.bluedragonmc.server.api.DatabaseConnectionStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Maps
import com.bluedragonmc.server.service.Messaging
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerPacketOutEvent
import net.minestom.server.instance.Instance
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.server.CachedPacket
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.packet.server.ServerPacket
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import net.minestom.testing.Env
import net.minestom.testing.EnvTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue

/**
 * Tests that [com.bluedragonmc.server.module.ScopedTabListModule] keeps each game's tab list scoped to that game.
 */
@EnvTest
class ScopedTabListTest {

    private val nameCounter = AtomicInteger()

    private class TestInstanceModule(private val env: Env) : InstanceModule() {
        lateinit var instanceA: Instance
        lateinit var instanceB: Instance

        override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {
            instanceA = env.createFlatInstance()
            instanceB = env.createFlatInstance()
        }

        override fun getSpawningInstance(player: Player): Instance = instanceA
        override fun ownsInstance(instance: Instance): Boolean = instance == instanceA || instance == instanceB
    }

    private class TestGame(private val env: Env) : Game(
        GameData("test", Maps.MapSource("test", "test", CommonTypes.MapFormat.UNRECOGNIZED, ""), null)
    ) {
        lateinit var instanceA: Instance
        lateinit var instanceB: Instance

        override fun initialize() {
            val instanceModule = TestInstanceModule(env)
            use(instanceModule)
            instanceA = instanceModule.instanceA
            instanceB = instanceModule.instanceB
        }
    }

    @BeforeEach
    fun setup() {
        Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
        Database.initialize(DatabaseConnectionStub())
    }

    /** A player connection that dispatches [PlayerPacketOutEvent] before recording a packet, unlike [net.minestom.testing.TestConnection]. */
    private class RecordingConnection : PlayerConnection() {

        private val packets = CopyOnWriteArrayList<ServerPacket>()
        private var online = true

        val received: List<ServerPacket> get() = packets.toList()

        fun clear() = packets.clear()

        fun tabListUpdatesMentioning(uuid: UUID): List<PlayerInfoUpdatePacket> =
            received.filterIsInstance<PlayerInfoUpdatePacket>().filter { packet ->
                packet.entries.any { it.uuid == uuid }
            }

        fun tabListRemovalsMentioning(uuid: UUID): List<PlayerInfoRemovePacket> =
            received.filterIsInstance<PlayerInfoRemovePacket>().filter { uuid in it.uuids }

        override fun sendPacket(packet: SendablePacket) {
            val serverPacket = when (packet) {
                is ServerPacket -> packet
                is CachedPacket -> packet.packet(serverState)
                else -> return
            }
            val event = PlayerPacketOutEvent(player, serverPacket)
            EventDispatcher.call(event)
            if (!event.isCancelled) packets.add(serverPacket)
        }

        override fun getRemoteAddress(): SocketAddress = InetSocketAddress("localhost", 25565)
        override fun isOnline(): Boolean = online
        override fun disconnect() {
            online = false
        }
    }

    private class TestClient(val player: Player, val connection: RecordingConnection)

    /**
     * Connects a player through the full login sequence using a [RecordingConnection].
     *
     * This is the same as [net.minestom.testing.TestConnectionImpl.connect], but it allows us
     * to swap the built-in [net.minestom.testing.TestConnection] with a [RecordingConnection].
     */
    private fun connect(env: Env, instance: Instance, pos: Pos = Pos(0.0, 42.0, 0.0)): TestClient {
        val profile = GameProfile(UUID.randomUUID(), "Player${nameCounter.incrementAndGet()}")
        val connection = RecordingConnection()
        val player = env.process().connection().createPlayer(connection, profile)
        player.eventNode().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance = instance
            event.player.respawnPoint = pos
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
        return TestClient(player, connection)
    }

    private fun createGame(env: Env) = TestGame(env).apply { init() }

    @Test
    fun `Players in different games cannot see each other`(env: Env) {
        val gameOne = createGame(env)
        val gameTwo = createGame(env)

        val alice = connect(env, gameOne.instanceA)
        gameOne.addPlayer(alice.player, sendPlayer = false)
        val bob = connect(env, gameTwo.instanceA)
        gameTwo.addPlayer(bob.player, sendPlayer = false)

        alice.connection.clear()
        bob.connection.clear()

        // A display name update is broadcast to every online player by Minestom. Alice must not receive Bob's.
        bob.player.setDisplayName(Component.text("Bob the Builder"))

        assertTrue(
            alice.connection.tabListUpdatesMentioning(bob.player.uuid).isEmpty(),
            "A player must not receive tab list updates for a player in another game",
        )
        assertTrue(
            bob.connection.tabListUpdatesMentioning(bob.player.uuid).isNotEmpty(),
            "A player must still receive their own tab list updates",
        )

        gameOne.endGame(queueAllPlayers = false)
        gameTwo.endGame(queueAllPlayers = false)
    }

    @Test
    fun `Players in the same game but different instances can see each other`(env: Env) {
        val game = createGame(env)

        val alice = connect(env, game.instanceA)
        game.addPlayer(alice.player, sendPlayer = false)

        val bob = connect(env, game.instanceB)
        game.addPlayer(bob.player, sendPlayer = false)

        assertTrue(
            alice.connection.tabListUpdatesMentioning(bob.player.uuid).isNotEmpty(),
            "Alice should receive Bob's tab list entry even though they are in different instances",
        )
        assertTrue(
            bob.connection.tabListUpdatesMentioning(alice.player.uuid).isNotEmpty(),
            "Bob should receive Alice's tab list entry even though they are in different instances",
        )

        game.endGame(queueAllPlayers = false)
    }

    @Test
    fun `Joining a game sends the game's members to the joining player`(env: Env) {
        val game = createGame(env)

        val alice = connect(env, game.instanceA)
        game.addPlayer(alice.player, sendPlayer = false)

        val bob = connect(env, game.instanceA)
        game.addPlayer(bob.player, sendPlayer = false)

        assertTrue(
            bob.connection.tabListUpdatesMentioning(alice.player.uuid).isNotEmpty(),
            "The joining player should be shown every existing member of the game",
        )

        game.endGame(queueAllPlayers = false)
    }

    @Test
    fun `A player joining a game is not leaked to players in other games`(env: Env) {
        val gameOne = createGame(env)
        val gameTwo = createGame(env)

        val alice = connect(env, gameOne.instanceA)
        gameOne.addPlayer(alice.player, sendPlayer = false)
        alice.connection.clear()

        // Connecting Bob broadcasts his tab list entry to every online player. Alice is in a different game,
        // so she must neither receive the add nor a follow-up removal (i.e. she must never see Bob at all).
        val bob = connect(env, gameTwo.instanceA)
        gameTwo.addPlayer(bob.player, sendPlayer = false)

        assertTrue(
            alice.connection.tabListUpdatesMentioning(bob.player.uuid).isEmpty(),
            "Alice must not receive Bob's tab list entry when he joins another game",
        )

        gameOne.endGame(queueAllPlayers = false)
        gameTwo.endGame(queueAllPlayers = false)
    }

    @Test
    fun `Leaving a game removes the player from the remaining members`(env: Env) {
        val gameOne = createGame(env)
        val gameTwo = createGame(env)

        val alice = connect(env, gameOne.instanceA)
        gameOne.addPlayer(alice.player, sendPlayer = false)
        val bob = connect(env, gameOne.instanceA)
        gameOne.addPlayer(bob.player, sendPlayer = false)

        alice.connection.clear()
        bob.connection.clear()

        // Moving Bob to another game removes him from game one.
        gameTwo.addPlayer(bob.player, sendPlayer = false)

        assertTrue(
            alice.connection.tabListRemovalsMentioning(bob.player.uuid).isNotEmpty(),
            "Remaining members should receive a removal for a player who left the game",
        )
        assertTrue(
            bob.connection.tabListRemovalsMentioning(alice.player.uuid).isNotEmpty(),
            "The leaving player should have the old game's members removed from their tab list",
        )

        gameOne.endGame(queueAllPlayers = false)
        gameTwo.endGame(queueAllPlayers = false)
    }

    @Test
    fun `Updates are shared between members of the same game`(env: Env) {
        val gameOne = createGame(env)
        val gameTwo = createGame(env)

        val alice = connect(env, gameOne.instanceA)
        gameOne.addPlayer(alice.player, sendPlayer = false)
        val bob = connect(env, gameOne.instanceA)
        gameOne.addPlayer(bob.player, sendPlayer = false)
        val carol = connect(env, gameTwo.instanceA)
        gameTwo.addPlayer(carol.player, sendPlayer = false)

        alice.connection.clear()
        bob.connection.clear()
        carol.connection.clear()

        // Bob (game one) changes his game mode. Only game one members should see it.
        bob.player.gameMode = GameMode.CREATIVE

        assertTrue(
            alice.connection.tabListUpdatesMentioning(bob.player.uuid).isNotEmpty(),
            "Members of the same game should receive each other's tab list updates",
        )
        assertTrue(
            carol.connection.tabListUpdatesMentioning(bob.player.uuid).isEmpty(),
            "Players in another game must not receive tab list updates for Bob",
        )

        gameOne.endGame(queueAllPlayers = false)
        gameTwo.endGame(queueAllPlayers = false)
    }
}
