package com.bluedragonmc.server.game

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.ModuleHolder
import com.bluedragonmc.server.api.DatabaseConnectionStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Maps
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.cancelOn
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.timer.TaskSchedule
import net.minestom.testing.Env
import net.minestom.testing.EnvTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnvTest
class ScopedTaskTest {

    private class TestEvent(game: ModuleHolder) : GameEvent(game)

    private class CounterModule : GameModule() {
        var runs = 0

        override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {
            buildTask { runs++ }.repeat(TaskSchedule.tick(1)).schedule()
        }
    }

    private class TestGame : Game(
        GameData(
            "test",
            Maps.MapSource("test", "test", CommonTypes.MapFormat.UNRECOGNIZED, ""),
            null,
        )
    ) {
        val counter = CounterModule()

        override fun initialize() {
            use(counter)
        }
    }

    @BeforeEach
    fun setup() {
        Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
        Database.initialize(DatabaseConnectionStub())
    }

    @Test
    fun `Module tasks stop when the module is unregistered`(env: Env) {
        val game = TestGame()
        game.init()

        env.tick()
        env.tick()
        env.tick()
        assertTrue(game.counter.runs > 0, "The module's task should run while the module is registered")

        val runsBeforeUnregister = game.counter.runs
        game.unregister(game.counter)

        env.tick()
        env.tick()
        env.tick()
        assertEquals(
            runsBeforeUnregister,
            game.counter.runs,
            "The module's task should not run after the module is unregistered",
        )
    }

    @Test
    fun `Game tasks stop when the game ends`(env: Env) {
        val game = TestGame()
        game.init()

        var runs = 0
        game.buildTask { runs++ }.repeat(TaskSchedule.tick(1)).schedule()

        env.tick()
        env.tick()
        assertTrue(runs > 0, "The game's task should run while the game is active")

        val runsBeforeEnd = runs
        game.endGame(queueAllPlayers = false)

        env.tick()
        env.tick()
        env.tick()
        assertEquals(runsBeforeEnd, runs, "The game's task should not run after the game ends")
    }

    @Test
    fun `cancelOn cancels the task when the event is triggered`(env: Env) {
        val game = TestGame()
        game.init()

        var runs = 0
        val task = game.buildTask { runs++ }
            .repeat(TaskSchedule.tick(1))
            .schedule()
            .cancelOn(game, TestEvent::class.java)

        env.tick()
        env.tick()
        assertTrue(runs > 0, "The task should run before the event is triggered")

        game.callEvent(TestEvent(game))

        assertFalse(task.isAlive(), "The task should be cancelled after the event is triggered")
    }
}
