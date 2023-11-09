package com.bluedragonmc.testing.utils

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.IncomingRPCHandlerStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.model.PlayerDocument
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.instance.Instance
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.network.player.PlayerConnection
import net.minestom.testing.Env
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.time.Duration
import java.util.*
import kotlin.coroutines.suspendCoroutine
import kotlin.test.assertTrue

object TestUtils {

    fun useDatabaseStub(): DatabaseConnectionStub {
        val conn = spyk<DatabaseConnectionStub>()
        Database.initialize(conn)
        return conn
    }

    fun useMessagingStubs(): Pair<IncomingRPCHandlerStub, OutgoingRPCHandlerStub> {
        val incoming = spyk<IncomingRPCHandlerStub>()
        val outgoing = spyk<OutgoingRPCHandlerStub>()
        Messaging.initializeIncoming(incoming)
        Messaging.initializeOutgoing(outgoing)
        return incoming to outgoing
    }

    fun emptyGame(env: Env, vararg modules: GameModule): Game {
        val game = EmptyGame(env)
        for (module in modules) {
            game.use(module)
        }
        game.checkUnmetDependencies()
        return game
    }

    fun addCustomPlayer(env: Env, game: Game): CustomPlayer = runBlocking {
        suspendCoroutine { continutation ->
            val player = createCustomPlayer()
            player.eventNode().addListener(PlayerLoginEvent::class.java) { event ->
                event.setSpawningInstance(game.getModule<InstanceModule>().getSpawningInstance(event.player))
            }
            env.process().connection().startPlayState(player, true).thenApply {
                env.process().connection().updateWaitingPlayers()
                game.addPlayer(player)
                continutation.resumeWith(Result.success(player))
            }
        }
    }

    fun startGame(game: Game) = game.callEvent(GameStartEvent(game))

    fun waitForSpawn(env: Env, player: Player) {
        val spawned = env.tickWhile({ player.instance == null }, Duration.ofSeconds(2))
        assertTrue(spawned, "Player spawned in the instance within 2 seconds")
    }

    private fun createCustomPlayer(): CustomPlayer {
        val player = CustomPlayer(UUID.randomUUID(), "RandName", StubPlayerConnection())
        player.data = spyk(PlayerDocument(player.uuid, player.username))
        coEvery { player.data.compute<MutableMap<String, Double>>(allAny(), allAny()) } answers {
            println("Player document had mocked field computed & updated")
        }
        return player
    }

    fun getInstance(game: Game): Instance {
        return (game.getModule<InstanceModule>() as FlatInstanceModule).getInstance()
    }

    private class EmptyGame(private val env: Env) : Game("empty", "empty", null) {

        override fun useMandatoryModules() {
            // no mandatory modules should be registered
        }

        override fun initialize() {
            use(FlatInstanceModule(env))
        }
    }

    class FlatInstanceModule(private val env: Env) : InstanceModule() {

        private lateinit var instance: Instance

        fun getInstance() = instance
        override fun getSpawningInstance(player: Player): Instance = this.instance
        override fun ownsInstance(instance: Instance): Boolean = instance == this.instance

        override fun initialize(parent: Game, eventNode: EventNode<Event>) {
            instance = env.createFlatInstance()
        }

    }

    private class StubPlayerConnection : PlayerConnection() {
        override fun sendPacket(packet: SendablePacket) {
            // Do nothing
        }

        override fun getRemoteAddress(): SocketAddress {
            return InetSocketAddress(0)
        }

    }
}