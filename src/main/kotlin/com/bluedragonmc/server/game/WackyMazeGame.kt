package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.InstantRespawnModule
import com.bluedragonmc.server.module.gameplay.InventoryPermissionsModule
import com.bluedragonmc.server.module.gameplay.WorldPermissionsModule
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.utils.ItemUtils
import net.kyori.adventure.text.Component
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.nio.file.Paths

class WackyMazeGame(mapName: String) : Game("WackyMaze", mapName) {
    init {
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(SharedInstanceModule())
        use(VoidDeathModule(32.0))
        use(CustomDeathMessageModule())
        use(CountdownModule(2, false,
            OldCombatModule(allowDamage = false, allowKnockback = true),
            SpectatorModule(spectateOnDeath = true)))
        use(WinModule(WinModule.WinCondition.LAST_PLAYER_ALIVE) { player, winningTeam ->
            if (player in winningTeam.players) 100 else 10
        })
        use(MOTDModule(Component.translatable("game.wackymaze.motd")))
        use(InstantRespawnModule())
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBlockInteract = false))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SpawnpointModule(SpawnpointModule.DatabaseSpawnpointProvider(/*Pos(-6.5, 64.0, 7.5), Pos(8.5, 64.0, -3.5)*/ allowRandomOrder = true)))
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false))
        use(TeamModule(true, TeamModule.AutoTeamMode.PLAYER_COUNT, 1))
        use(WackyMazeStickModule())
        use(AwardsModule())
        use(StatisticsModule())

        ready()
    }
}

class WackyMazeStickModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(GameStartEvent::class.java) { event ->

            val stickItem = ItemUtils.knockbackStick(10)

            parent.players.forEach { player ->
                player.inventory.setItemStack(0, stickItem)
            }
        }
    }
}
