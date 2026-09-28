package com.bluedragonmc.server.game

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.GameRegistry
import com.bluedragonmc.server.api.DatabaseConnectionStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Maps
import com.bluedragonmc.server.service.Messaging
import net.minestom.testing.Env
import net.minestom.testing.EnvTest
import net.minestom.testing.TestUtils.waitUntilCleared
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnvTest
class GameGarbageCollectionTest {

    private class TestGame : Game(
        GameData(
            "test",
            Maps.MapSource("test", "test", CommonTypes.MapFormat.UNRECOGNIZED, ""),
            null,
        )
    ) {
        override fun initialize() {}
    }

    @BeforeEach
    fun setup() {
        Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
        Database.initialize(DatabaseConnectionStub())
    }

    @Test
    fun `Ending a game detaches its event node from the global event handler`(env: Env) {
        val game = TestGame()
        game.init()

        assertTrue(
            env.process().eventHandler().children.any { it.name == nodeName(game) },
            "Game event node should be attached while the game is running",
        )

        game.endGame(queueAllPlayers = false)

        assertFalse(
            env.process().eventHandler().children.any { it.name == nodeName(game) },
            "Game event node should be detached after the game ends",
        )
    }

    @Test
    @Suppress("UNUSED_PARAMETER")
    fun `Ended games can be garbage collected`(env: Env) {
        var game: TestGame? = TestGame()
        game!!.init()
        game.endGame(queueAllPlayers = false)

        val reference = WeakReference(game)
        game = null

        waitUntilCleared(reference)
    }

    @Test
    fun `Games with finished tasks can be garbage collected after being removed from the registry`(env: Env) {
        var game: TestGame? = TestGame()
        requireNotNull(game).init()

        // The task body references the game, so a task retained by the scheduler or a task
        // scope after it has finished would keep the game from being collected.
        val task = game.buildTask { game?.id }.schedule()
        env.tick()

        assertFalse(task.isAlive, "The one-shot task should have finished after one tick")

        game.endGame(queueAllPlayers = false)
        assertFalse(
            GameRegistry.games.any { it === game },
            "The game should be removed from the registry after it ends",
        )

        val reference = WeakReference(game)
        game = null

        waitUntilCleared(reference)
    }

    private fun nodeName(game: Game) = "${game.id}-${game.data}"
}
