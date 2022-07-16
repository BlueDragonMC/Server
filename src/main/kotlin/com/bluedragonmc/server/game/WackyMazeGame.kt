package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.module.minigame.WinModule
import net.kyori.adventure.text.Component
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.nio.file.Paths

class WackyMazeGame(mapName: String) : Game("WackyMaze", mapName) {
    init {
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(SharedInstanceModule())
        use(VoidDeathModule(32.0))
        use(CountdownModule(2, false,
            OldCombatModule(allowDamage = false, allowKnockback = true),
            SpectatorModule(spectateOnDeath = true)))
        use(WinModule(WinModule.WinCondition.LAST_PLAYER_ALIVE) { player, winningTeam ->
            if (player in winningTeam.players) 100 else 10
        })
        use(MOTDModule(Component.text("Each player will receive a knockback stick.\n" + "Use it to wack your enemies off the map!\n" + "The last player alive wins!")))
        use(InstantRespawnModule())
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBlockInteract = false))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SpawnpointModule(SpawnpointModule.DatabaseSpawnpointProvider(/*Pos(-6.5, 64.0, 7.5), Pos(8.5, 64.0, -3.5)*/ allowRandomOrder = true, callback = { ready() })))
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false, forcedItemSlot = 0))
        use(TeamModule(true, TeamModule.AutoTeamMode.PLAYER_COUNT, 1))
        use(WackyMazeStickModule())
        use(AwardsModule())
    }
}

class WackyMazeStickModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(GameStartEvent::class.java) { event ->

            val stickItem = ItemStack.builder(Material.STICK).displayName(Component.text("Knockback Stick"))
                .lore(Component.text("Use this to wack your enemies"), Component.text("off the map!"))
                .meta { metaBuilder: ItemMeta.Builder ->
                    metaBuilder.enchantment(Enchantment.KNOCKBACK, 10)
                }.build()

            parent.players.forEach { player ->
                player.inventory.setItemStack(0, stickItem)
            }
        }
    }

}

/* TODO STUFF:
ChestLootTableModule
AwardModule
AchievementModule

OldCombatModule
ModernCombatModule

MaxPlayersModule (should be mandatory for games in the future)

 */