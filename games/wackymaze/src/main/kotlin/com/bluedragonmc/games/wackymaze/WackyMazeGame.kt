package com.bluedragonmc.games.wackymaze

import com.bluedragonmc.games.wackymaze.module.WackyMazeStickModule
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GlobalCosmeticModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.database.CosmeticsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.InstantRespawnModule
import com.bluedragonmc.server.module.gameplay.InventoryPermissionsModule
import com.bluedragonmc.server.module.gameplay.WorldPermissionsModule
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import net.kyori.adventure.text.Component
import net.minestom.server.entity.GameMode
import net.minestom.server.item.Material
import java.nio.file.Paths

class WackyMazeGame(mapName: String) : Game("WackyMaze", mapName) {
    init {
        // MAP
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))

        // INSTANCE
        use(SharedInstanceModule())

        // COMBAT
        use(CustomDeathMessageModule())

        // GAMEPLAY
        use(InstantRespawnModule())
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBlockInteract = false))
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false))

        // MINIGAME
        use(CountdownModule(2, false,
            OldCombatModule(allowDamage = false, allowKnockback = true),
            SpectatorModule(spectateOnDeath = true)))
        use(VoidDeathModule(32.0))
        use(WinModule(WinModule.WinCondition.LAST_PLAYER_ALIVE) { player, winningTeam ->
            if (player in winningTeam.players) 100 else 10
        })
        use(MOTDModule(Component.translatable("game.wackymaze.motd")))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SpawnpointModule(SpawnpointModule.DatabaseSpawnpointProvider(allowRandomOrder = true)))
        use(TeamModule(true, TeamModule.AutoTeamMode.PLAYER_COUNT, 1))

        // CUSTOM
        use(WackyMazeStickModule())

        // DATABASE
        use(StatisticsModule())
        use(AwardsModule())

        // COSMETICS
        use(ConfigModule())
        use(CosmeticsModule())
        use(GlobalCosmeticModule())

        ready()
    }

    enum class StickItem(override val id: String, val material: Material) : CosmeticsModule.Cosmetic {
        FISH("wackymaze_stick_fish", Material.COD),
        DOOR("wackymaze_stick_door", Material.OAK_DOOR),
        BAMBOO("wackymaze_stick_bamboo", Material.BAMBOO),
        NETHERITE_HOE("wackymaze_stick_hoe", Material.NETHERITE_HOE),
        DEAD_BUSH("wackymaze_stick_deadbush", Material.DEAD_BUSH),
    }
}
