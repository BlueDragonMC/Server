package com.bluedragonmc.server.game

import com.bluedragonmc.games.arenapvp.ArenaPvpGame
import com.bluedragonmc.games.bedwars.BedWarsGame
import com.bluedragonmc.games.fastfall.FastFallGame
import com.bluedragonmc.games.infection.InfectionGame
import com.bluedragonmc.games.infinijump.InfinijumpGame
import com.bluedragonmc.games.lobby.Lobby
import com.bluedragonmc.games.pvpmaster.PvpMasterGame
import com.bluedragonmc.games.skyfall.SkyfallGame
import com.bluedragonmc.games.skywars.SkyWarsGame
import com.bluedragonmc.games.teamdeathmatch.TeamDeathmatchGame
import com.bluedragonmc.games.wackymaze.WackyMazeGame
import com.bluedragonmc.server.Database
import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.NAMESPACE
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.database.MapData
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.queue.LocalTestingEnvironment
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.testing.utils.DatabaseConnectionStub
import io.mockk.*
import net.minestom.server.MinecraftServer
import net.minestom.server.api.Env
import net.minestom.server.api.EnvTest
import net.minestom.server.exception.ExceptionManager
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import net.minestom.server.world.DimensionTypeManager
import org.junit.jupiter.api.*
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnvTest
class GameIntegrationTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            Environment.current = LocalTestingEnvironment()

            val dbStub = mockk<DatabaseConnectionStub>()
            Database.initialize(dbStub)
            mockkConstructor(DimensionTypeManager::class)
            mockkConstructor(ExceptionManager::class)

            every { anyConstructed<ExceptionManager>().handleException(any()) } answers {
                fail(it.invocation.args.first() as Throwable)
            }

            every { anyConstructed<DimensionTypeManager>().getDimension(any()) } answers {
                callOriginal() ?: DimensionType.builder(NamespaceID.from("$NAMESPACE:fullbright_dimension")).ambientLight(2f).build().also {
                    MinecraftServer.getDimensionTypeManager().addDimension(it)
                }
            }

            coEvery { dbStub.getMapOrNull(any()) } answers {
                MapData(
                    "Mock",
                    description = "Testing environment",
                    additionalLocations = List(100) { emptyList() }
                )
            }
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            unmockkAll()
        }
    }

    @Test
    fun `ArenaPvP initialization`(env: Env) {
        testInit(::ArenaPvpGame)
    }

    @Test
    fun `BedWars initialization`(env: Env) {
        testInit(::BedWarsGame)
    }

    @Test
    fun `FastFall initialization`(env: Env) {
        testInit(::FastFallGame)
    }

    @Test
    fun `Infection initialization`(env: Env) {
        testInit(::InfectionGame)
    }

    @Test
    fun `Infinijump initialization`(env: Env) {
        testInit(::InfinijumpGame)
    }

    @Test
    fun `Lobby initialization`(env: Env) {
        testInit { Lobby() }
    }

    @Test
    fun `PvPMaster initialization`(env: Env) {
        testInit(::PvpMasterGame)
    }

    @Test
    fun `Skyfall initialization`(env: Env) {
        testInit(::SkyfallGame)
    }

    @Test
    fun `Skywars initialization`(env: Env) {
        testInit(::SkyWarsGame)
    }

    @Test
    fun `TeamDeathmatch initialization`(env: Env) {
        testInit(::TeamDeathmatchGame)
    }

    @Test
    fun `WackyMaze initialization`(env: Env) {
        testInit(::WackyMazeGame)
    }

    private fun testInit(init: (String) -> Game) {
        val game = assertDoesNotThrow { init("mock") } // Initialize the game
        assertEquals(GameState.WAITING, game.state)
        assertTrue(Game.games.contains(game))
        assertNotNull(game.getInstanceOrNull()) // Make sure the game has an instance
        game.callEvent(GameStartEvent(game))
        assertTrue(game.modules.isNotEmpty())
        if (game.hasModule<CountdownModule>()) {
            assertEquals(GameState.INGAME, game.state)
        }
        game.endGame(Duration.ZERO)
        assertEquals(GameState.ENDING, game.state)
        assertFalse(Game.games.contains(game))
    }

}