package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.module.minigame.WinModule
import net.kyori.adventure.text.Component
import net.minestom.server.entity.GameMode
import java.nio.file.Paths

class PvpMasterGame(mapName: String) : Game("PvPMaster", mapName) {
    init {
        // COMBAT
        use(CustomDeathMessageModule())
        use(OldCombatModule())

        // GAMEPLAY
        use(ArmorLevelsModule())
        use(DoubleJumpModule(verticalStrength = 5.0))
        use(FallDamageModule)
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false))
        use(MOTDModule(Component.text("You start out with netherite armor.\nFor each kill, your armor downgrades one level.\nThe first player to get a kill with\nleather armor is the winner.")))
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

        ready()
    }
}