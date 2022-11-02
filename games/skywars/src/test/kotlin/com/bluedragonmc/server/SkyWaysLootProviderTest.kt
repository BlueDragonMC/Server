package com.bluedragonmc.server

import com.bluedragonmc.games.skywars.SkyWarsGame
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.gameplay.ChestLootModule
import com.bluedragonmc.server.module.vanilla.ChestModule
import com.bluedragonmc.testing.utils.TestUtils
import net.minestom.server.api.Env
import net.minestom.server.api.EnvTest
import net.minestom.server.event.EventNode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

@EnvTest
class SkyWaysLootProviderTest {

    @Test
    fun skyWarsLootProvider(env: Env) {
        val game = TestUtils.emptyGame(env)

        val config = ConfigModule("skywars.yml").apply {
            initialize(game, EventNode.all("stub"))
        }.getConfig()
        val provider = SkyWarsGame.NormalSkyWarsLootProvider(config, game)

        game.useModules(listOf(
            GuiModule(),
            ChestModule(),
            ChestLootModule(provider)
        ))

        assertDoesNotThrow("Error gathering spawn loot") {
            provider.getSpawnLoot()
        }
        assertDoesNotThrow("Error gathering mid loot") {
            provider.getMidLoot()
        }
    }
}