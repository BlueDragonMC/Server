package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.FallDamageModule
import com.bluedragonmc.server.module.vanilla.NaturalRegenerationModule
import net.kyori.adventure.text.Component
import net.minestom.server.entity.GameMode
import java.nio.file.Paths

class PvpMasterGame(mapName: String) : Game("PvPMaster", mapName) {
    init {

        val config = use(ConfigModule("pvpmaster.yml")).getConfig()
        val levels = config.node("levels").getList(KitsModule.Kit::class.java)!!

        // COMBAT
        use(CustomDeathMessageModule())
        use(OldCombatModule())

        // GAMEPLAY
        use(ArmorLevelsModule(levels))
        use(DoubleJumpModule(verticalStrength = 5.0, cooldownMillis = 4000))
        use(FallDamageModule)
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false))
        use(MOTDModule(Component.translatable("game.pvpmaster.motd")))
        use(NaturalRegenerationModule())
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SidebarModule(name))
        use(SpawnpointModule(spawnpointProvider = SpawnpointModule.DatabaseSpawnpointProvider(allowRandomOrder = true)))
        use(SpectatorModule(spectateOnDeath = false, spectateOnLeave = true))
        use(TimedRespawnModule(seconds = 3))
        use(WorldPermissionsModule())

        // INSTANCE
        use(SharedInstanceModule())

        // MAP
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))

        // MINIGAME
        use(CountdownModule(threshold = 2, allowMoveDuringCountdown = true, countdownSeconds = 10, useOnStart = arrayOf(OldCombatModule())))
        use(WinModule(WinModule.WinCondition.LAST_PLAYER_ALIVE) { player, winningTeam -> // "Last player alive" so if players leave the game, a winner is still declared
            if (player in winningTeam.players) 50 else 5
        })
        use(AwardsModule())

        use(StatisticsModule())

        ready()
    }
}