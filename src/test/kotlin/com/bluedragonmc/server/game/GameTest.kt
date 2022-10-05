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
import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.NAMESPACE
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.MapData
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.queue.LocalTestingEnvironment
import com.bluedragonmc.server.utils.GameState
import io.mockk.coEvery
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import net.minestom.server.MinecraftServer
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import org.junit.jupiter.api.*
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameTest {

    @BeforeEach
    fun setup() {
        Environment.current = LocalTestingEnvironment()
        MinecraftServer.init().start("127.0.0.1", 0)

        mockkObject(DatabaseModule)
        mockkConstructor(DatabaseModule::class)

        coEvery { anyConstructed<DatabaseModule>().getMapOrNull(any()) } answers {
            MapData(
                "Mock",
                description = "Testing environment",
                additionalLocations = List(100) { emptyList() }
            )
        }

        // Register server-wide fullbright dimension necessary for some games
        MinecraftServer.getDimensionTypeManager().addDimension(
            DimensionType.builder(NamespaceID.from("$NAMESPACE:fullbright_dimension")).ambientLight(2f).build()
        )

        // Any exception should cause the current test to fail
        MinecraftServer.getExceptionManager().setExceptionHandler {
            fail(it)
        }
    }

    @AfterEach
    fun cleanup() {
        MinecraftServer.stopCleanly()
        unmockkAll()
    }

    @Test
    fun `ArenaPvP initialization`() {
        testInit(::ArenaPvpGame)
    }

    @Test
    fun `BedWars initialization`() {
        testInit(::BedWarsGame)
    }

    @Test
    fun `FastFall initialization`() {
        testInit(::FastFallGame)
    }

    @Test
    fun `Infection initialization`() {
        testInit(::InfectionGame)
    }

    @Test
    fun `Infinijump initialization`() {
        testInit(::InfinijumpGame)
    }

    @Test
    fun `Lobby initialization`() {
        testInit { Lobby() }
    }

    @Test
    fun `PvPMaster initialization`() {
        testInit(::PvpMasterGame)
    }

    @Test
    fun `Skyfall initialization`() {
        testInit(::SkyfallGame)
    }

    @Test
    fun `Skywars initialization`() {
        testInit(::SkyWarsGame)
    }

    @Test
    fun `TeamDeathmatch initialization`() {
        testInit(::TeamDeathmatchGame)
    }

    @Test
    fun `WackyMaze initialization`() {
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