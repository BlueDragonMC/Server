package com.bluedragonmc.server.game

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.service.Maps
import net.minestom.testing.Env
import net.minestom.testing.EnvTest
import net.minestom.testing.TestUtils.waitUntilCleared
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
        override fun useMandatoryModules() {}
        override fun initialize() {}
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

    private fun nodeName(game: Game) = "${game.id}-${game.data}"
}
