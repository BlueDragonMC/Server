package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.MiniGameModule
import com.bluedragonmc.server.module.minigame.WinModule
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.nio.file.Paths

class WackyMazeGame : Game("WackyMaze") {
    init {
        use(AnvilFileMapProviderModule(Paths.get("test_map")))
        use(SharedInstanceModule())
        use(VoidDeathModule(32.0))
        use(
            MiniGameModule(
                countdownThreshold = 2,
                winCondition = WinModule.WinCondition.LAST_PLAYER_ALIVE,
                motd = Component.text(
                    "Each player will receive a knockback stick.\n" +
                            "Use it to wack your enemies off the map!\n" +
                            "The last player alive wins!"
                )
            )
        )
        use(SpectatorModule(spectateOnDeath = true))
        use(OldCombatModule(allowDamage = false, allowKnockback = true))
        use(InstantRespawnModule())
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBlockInteract = false))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SpawnpointModule(SpawnpointModule.TestSpawnpointProvider(Pos(-6.5, 64.0, 7.5), Pos(8.5, 64.0, -3.5))))
        use(WackyMazeStickModule())
    }
}

class WackyMazeStickModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(GameStartEvent::class.java) { event ->

            val stickItem = ItemStack.builder(Material.STICK)
                .displayName(Component.text("Knockback Stick"))
                .lore(Component.text("Use this to wack your enemies"), Component.text("off the map!"))
                .meta { metaBuilder: ItemMeta.Builder ->
                    metaBuilder.enchantment(Enchantment.KNOCKBACK, 10)
                }
                .build()

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