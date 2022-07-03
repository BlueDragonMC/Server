package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.MiniGameModule
import com.bluedragonmc.server.module.minigame.WinModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.utils.inventory.PlayerInventoryUtils
import java.nio.file.Paths

class TeamDeathmatchGame : Game("Team Deathmatch") {
    init {
        use(AnvilFileMapProviderModule(Paths.get("test_map")))
        use(SharedInstanceModule())
        use(VoidDeathModule(32.0))
        use(
            MiniGameModule(
                countdownThreshold = 2,
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

        val ironHelmet = ItemStack.builder(Material.IRON_HELMET).build()
        val ironChestplate = ItemStack.builder(Material.IRON_CHESTPLATE).build()
        val ironLeggings = ItemStack.builder(Material.IRON_LEGGINGS).build()
        val ironBoots = ItemStack.builder(Material.IRON_BOOTS).build()
        val sword = ItemStack.builder(Material.DIAMOND_SWORD).build()
        use(KitsModule(showMenu = true, selectableKits = listOf(KitsModule.Kit(
            name = Component.text("Default", NamedTextColor.YELLOW),
            icon = Material.DIAMOND_CHESTPLATE,
            items = hashMapOf(
                Pair(0, sword),
                Pair(PlayerInventoryUtils.HELMET_SLOT, ironHelmet),
                Pair(PlayerInventoryUtils.CHESTPLATE_SLOT, ironChestplate),
                Pair(PlayerInventoryUtils.LEGGINGS_SLOT, ironLeggings),
                Pair(PlayerInventoryUtils.BOOTS_SLOT, ironBoots)
        )), KitsModule.Kit(
            name = Component.text("Hard Mode", NamedTextColor.RED),
            icon = Material.REDSTONE,
            items = hashMapOf(
                Pair(0, sword),
                Pair(PlayerInventoryUtils.BOOTS_SLOT, ironBoots)
        )))))
    }
}