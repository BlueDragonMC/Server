package com.bluedragonmc.server.game

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.ModuleHolder
import com.bluedragonmc.server.api.DatabaseConnectionStub
import com.bluedragonmc.server.api.OutgoingRPCHandlerStub
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.PlayerListModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Maps
import com.bluedragonmc.server.service.Messaging
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.testing.EnvTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

@EnvTest
class GameModuleTeardownTest {

    @DependsOn(PlayerListModule::class)
    private class ProviderUsingModule : GameModule() {
        var playersAtTeardown: List<Player>? = null

        override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {}

        override fun deinitialize() {
            playersAtTeardown = players
        }
    }

    private class TestGame : Game(
        GameData(
            "test",
            Maps.MapSource("test", "test", CommonTypes.MapFormat.UNRECOGNIZED, ""),
            null,
        )
    ) {
        val providerUsingModule = ProviderUsingModule()

        override fun initialize() {
            use(providerUsingModule)
        }
    }

    @BeforeEach
    fun setup() {
        Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
        Database.initialize(DatabaseConnectionStub())
    }

    @Test
    fun `Provider modules are available while other modules deinitialize`() {
        val game = TestGame()
        game.init()

        game.endGame(queueAllPlayers = false)

        assertNotNull(
            game.providerUsingModule.playersAtTeardown,
            "PlayerListModule should still be registered during module teardown",
        )
    }
}
