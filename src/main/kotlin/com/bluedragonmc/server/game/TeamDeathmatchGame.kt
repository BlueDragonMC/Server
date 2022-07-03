package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.MiniGameModule
import com.bluedragonmc.server.module.minigame.WinModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.nio.file.Paths

class TeamDeathmatchGame : Game("Team Deathmatch") {
    init {
        use(AnvilFileMapProviderModule(Paths.get("test_map")))
        use(SharedInstanceModule())
        use(VoidDeathModule(32.0))
        use(
            MiniGameModule(
                countdownThreshold = 1,
                winCondition = WinModule.WinCondition.LAST_TEAM_ALIVE,
                motd = Component.text(
                    "Two teams battle it out\n" +
                            "until only one team stands!\n"
                )
            )
        )
        use(SpectatorModule(spectateOnDeath = true))
        use(OldCombatModule(allowDamage = true, allowKnockback = true))
        use(InstantRespawnModule())
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBlockInteract = false))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SpawnpointModule(SpawnpointModule.TestSpawnpointProvider(Pos(-6.5, 64.0, 7.5), Pos(8.5, 64.0, -3.5))))
        use(TeamModule(autoTeams = true, autoTeamMode = TeamModule.AutoTeamMode.TEAM_COUNT, 2))
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false, forcedItemSlot = null))

        use(GuiModule())
        val menu = getModule<GuiModule>().createMenu(Component.text("WackyMaze Shop"), InventoryType.CHEST_6_ROW) {
            // This block is a builder for the menu's slots. Use the `slot` method to create a new slot, and it is immediately added to the menu.
            slot(pos(6, 5), Material.BARRIER, {
                // This block's context is Minestom's `ItemStack.Builder`, so all of its methods can be used without method chaining or running `build()`
                displayName(Component.text("Close", NamedTextColor.RED))
            }) {
                // The second block passed to the `slot` method is the action that will be triggered when the slot is clicked. It receives a `SlotClickEvent`.
                menu.close(player)
            } // More slots can be registered exactly the same way using the `slot` method.
        }


        use(object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                val ironHelmet = ItemStack.builder(Material.IRON_HELMET).build()
                val ironChestplate = ItemStack.builder(Material.IRON_CHESTPLATE).build()
                val ironLeggings = ItemStack.builder(Material.IRON_LEGGINGS).build()
                val ironBoots = ItemStack.builder(Material.IRON_BOOTS).build()
                val sword = ItemStack.builder(Material.DIAMOND_SWORD).build()
                eventNode.addListener(GameStartEvent::class.java) { event ->
                    players.forEach {
                        it.inventory.helmet = ironHelmet
                        it.inventory.chestplate = ironChestplate
                        it.inventory.leggings = ironLeggings
                        it.inventory.boots = ironBoots
                        it.inventory.setItemStack(0, sword)
                    }
                }
            }
        })
    }
}