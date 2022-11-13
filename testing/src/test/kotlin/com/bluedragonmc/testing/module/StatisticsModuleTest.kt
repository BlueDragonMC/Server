package com.bluedragonmc.testing.module

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Database
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.testing.utils.DatabaseConnectionStub
import com.bluedragonmc.testing.utils.TestUtils
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.minestom.server.api.Env
import net.minestom.server.api.EnvTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnvTest
class StatisticsModuleTest {

    private fun mock() {
        val mock = mockk<DatabaseConnectionStub>()
        Database.initialize(mock)
    }

    private fun setup(env: Env): Pair<CustomPlayer, StatisticsModule> {
        mock()
        val module = StatisticsModule()
        val game = TestUtils.emptyGame(env, module)
        val player = TestUtils.addCustomPlayer(env, game)

        return player to module
    }

    @Test
    fun `recordStatisticIfGreater with no existing value`(env: Env) {
        mock()
        val (player, module) = setup(env)

        // Should always record when there's no existing value
        var recorded = false
        runBlocking {
            module.recordStatisticIfGreater(player, "test.statistic", 5.0) {
                recorded = true
            }
        }
        assertTrue(recorded, "Statistic should have been recorded (No existing value)")
    }

    @Test
    fun `recordStatisticIfGreater with larger existing value`(env: Env) {
        mock()
        val (player, module) = setup(env)
        // Should not record when the new value is less than the existing value
        player.data.statistics["test.statistic"] = 10.0
        var recorded = false
        runBlocking {
            module.recordStatisticIfGreater(player, "test.statistic", 5.0) {
                recorded = true
            }
        }
        assertFalse(recorded, "Statistic should not have been recorded (5.0 < 10.0)")
    }

    @Test
    fun `recordStatisticIfGreater with smaller existing value`(env: Env) {
        mock()
        val (player, module) = setup(env)
        // Should record when the new value is greater than the existing value
        player.data.statistics["test.statistic"] = 5.0
        var recorded = false
        runBlocking {
            module.recordStatisticIfGreater(player, "test.statistic", 10.0) {
                recorded = true
            }
        }
        assertTrue(recorded, "Statistic should have been recorded (10.0 > 5.0)")
    }

    @Test
    fun `recordStatisticIfLower with no existing value`(env: Env) {
        mock()
        val (player, module) = setup(env)

        // Should always record when there's no existing value
        var recorded = false
        runBlocking {
            module.recordStatisticIfLower(player, "test.statistic", 5.0) {
                recorded = true
            }
        }
        assertTrue(recorded, "Statistic should have been recorded (No existing value)")
    }

    @Test
    fun `recordStatisticIfLower with larger existing value`(env: Env) {
        mock()
        val (player, module) = setup(env)
        // Should not record when the new value is less than the existing value
        player.data.statistics["test.statistic"] = 10.0
        var recorded = false
        runBlocking {
            module.recordStatisticIfLower(player, "test.statistic", 5.0) {
                recorded = true
            }
        }
        assertTrue(recorded, "Statistic should have been recorded (5.0 < 10.0)")
    }

    @Test
    fun `recordStatisticIfLower with smaller existing value`(env: Env) {
        mock()
        val (player, module) = setup(env)
        // Should record when the new value is Lower than the existing value
        player.data.statistics["test.statistic"] = 5.0
        var recorded = false
        runBlocking {
            module.recordStatisticIfLower(player, "test.statistic", 10.0) {
                recorded = true
            }
        }
        assertFalse(recorded, "Statistic should not have been recorded (10.0 > 5.0)")
    }

}