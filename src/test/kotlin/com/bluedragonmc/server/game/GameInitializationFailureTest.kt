package com.bluedragonmc.server.game

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.GameRegistry
import com.bluedragonmc.server.ModuleHolder
import com.bluedragonmc.server.api.DatabaseConnectionStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Maps
import com.bluedragonmc.server.service.Messaging
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.testing.Env
import net.minestom.testing.EnvTest
import net.minestom.testing.TestUtils.waitUntilCleared
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnvTest
class GameInitializationFailureTest {

    private class TestInstanceModule : InstanceModule() {
        lateinit var instance: InstanceContainer
        var deinitialized = false

        override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {
            instance = MinecraftServer.getInstanceManager().createInstanceContainer()
        }

        override fun getSpawningInstance(player: Player): Instance = instance

        override fun ownsInstance(instance: Instance): Boolean =
            this::instance.isInitialized && this.instance == instance

        override fun deinitialize() {
            deinitialized = true
        }
    }

    private class FailingModule : GameModule() {
        var deinitialized = false

        override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {
            error("This module always fails to initialize")
        }

        override fun deinitialize() {
            deinitialized = true
        }
    }

    private class FailingGame : Game(
        GameData(
            "test",
            Maps.MapSource("test", "test", CommonTypes.MapFormat.UNRECOGNIZED, ""),
            null,
        )
    ) {
        val instanceModule = TestInstanceModule()
        val failingModule = FailingModule()

        override fun initialize() {
            use(instanceModule)
            use(failingModule)
        }
    }

    private class FailingAfterRegisteringInstanceGame : Game(
        GameData(
            "test",
            Maps.MapSource("test", "test", CommonTypes.MapFormat.UNRECOGNIZED, ""),
            null,
        )
    ) {
        val instanceModule = TestInstanceModule()

        override fun initialize() {
            use(instanceModule)
            error("The game fails after registering its instance")
        }
    }

    @BeforeEach
    fun setup() {
        Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
        Database.initialize(DatabaseConnectionStub())
    }

    @Test
    fun `A module that fails to initialize is removed`(env: Env) {
        val game = FailingGame()

        assertFailsWith<IllegalStateException> { game.init() }
        repeat(2) { env.tick() }

        assertTrue(game.failingModule.deinitialized, "The failed module should be deinitialized")
        assertFalse(GameRegistry.games.any { it === game }, "A failed game must not stay in the registry")
        assertTrue(game.rootEventNode.children.isEmpty(), "Failed module event nodes should be removed")
        assertFalse(
            env.process().instance().instances.contains(game.instanceModule.instance),
            "Instances owned by a failed game should be unregistered",
        )
        assertTrue(game.instanceModule.deinitialized, "Modules registered before the failure should be deinitialized")
    }

    @Test
    fun `A game that fails after registering an instance is removed`(env: Env) {
        val game = FailingAfterRegisteringInstanceGame()

        assertFailsWith<IllegalStateException> { game.init() }
        repeat(2) { env.tick() }

        assertFalse(GameRegistry.games.any { it === game }, "A failed game must not stay in the registry")
        assertTrue(game.rootEventNode.children.isEmpty(), "Module event nodes should be removed")
        assertTrue(game.instanceModule.deinitialized, "Registered modules should be deinitialized")
        assertFalse(
            env.process().instance().instances.contains(game.instanceModule.instance),
            "Instances owned by a failed game should be unregistered",
        )
    }

    @Test
    @Suppress("UNUSED_PARAMETER")
    fun `The original initialization failure is rethrown`(env: Env) {
        val game = FailingAfterRegisteringInstanceGame()

        val error = assertFailsWith<IllegalStateException> { game.init() }

        assertEquals("The game fails after registering its instance", error.message)
    }

    @Test
    fun `Failed games can be garbage collected`(env: Env) {
        var game: FailingAfterRegisteringInstanceGame? = FailingAfterRegisteringInstanceGame()

        assertFailsWith<IllegalStateException> { game?.init() }
        repeat(2) { env.tick() }

        val reference = WeakReference(game)
        game = null

        waitUntilCleared(reference)
    }
}
