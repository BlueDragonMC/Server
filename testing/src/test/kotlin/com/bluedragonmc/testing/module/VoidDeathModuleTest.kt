package com.bluedragonmc.testing.module

import com.bluedragonmc.server.module.minigame.VoidDeathModule
import com.bluedragonmc.testing.utils.TestUtils
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyAll
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.testing.Env
import net.minestom.testing.EnvTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnvTest
class VoidDeathModuleTest {

    @Test
    fun `Void death in normal mode`(env: Env) {
        val game = TestUtils.emptyGame(env, VoidDeathModule(0.0, false))
        val instance = TestUtils.getInstance(game)

        val pos = Pos(0.0, -10.0, 0.0)
        val player = env.createPlayer(instance, pos)
        TestUtils.waitForSpawn(env, player)
        env.process().eventHandler().call(PlayerMoveEvent(player, pos, true))

        assertTrue(player.isDead, "Player was not killed by VoidDeathModule while in the void")
    }

    @Test
    fun `Void death in respawn mode`(env: Env) {
        val game = TestUtils.emptyGame(env, VoidDeathModule(0.0, true))
        val instance = TestUtils.getInstance(game)

        val pos = Pos(0.0, -10.0, 0.0)
        val connection = env.createPlayer(instance, pos)
        TestUtils.waitForSpawn(env, connection)
        val player = spyk(connection)
        env.process().eventHandler().call(PlayerMoveEvent(player, pos, true))

        // Verify the player was respawned
        verify {
            player.respawn()
        }

        // Verify the player wasn't killed
        verifyAll(inverse = true) {
            player.kill()
        }
        assertFalse(player.isDead, "Player was killed by VoidDeathModule in respawn mode")
    }

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

}