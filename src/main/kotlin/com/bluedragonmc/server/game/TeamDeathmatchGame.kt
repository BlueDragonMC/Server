package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.module.minigame.WinModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.utils.inventory.PlayerInventoryUtils
import java.nio.file.Paths

class TeamDeathmatchGame(mapName: String) : Game("Team Deathmatch", mapName) {
    init {
        use(AnvilFileMapProviderModule(Paths.get("test_map")))
        use(SharedInstanceModule())
        use(VoidDeathModule(32.0))
        use(CountdownModule(2, false,
            OldCombatModule(allowDamage = true, allowKnockback = true),
            SpectatorModule(spectateOnDeath = true)))
        use(WinModule(WinModule.WinCondition.LAST_TEAM_ALIVE) { player, winningTeam ->
            if (player in winningTeam.players) 150 else 15
        })
        use(MOTDModule(Component.text("Two teams battle it out\n" + "until only one team stands!\n")))
        use(InstantRespawnModule())
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBlockInteract = false))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SpawnpointModule(SpawnpointModule.TestSpawnpointProvider(Pos(8.5, 72.0, 11.5), Pos(11.5, 72.0, -2.5), Pos(40.5, 75.0, 12.5), Pos(21.5, 72.93750, 22.5))))
        use(TeamModule(autoTeams = true, autoTeamMode = TeamModule.AutoTeamMode.TEAM_COUNT, 2))
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false, forcedItemSlot = null))

        val ironHelmet = ItemStack.builder(Material.IRON_HELMET).build()
        val ironChestplate = ItemStack.builder(Material.IRON_CHESTPLATE).build()
        val ironLeggings = ItemStack.builder(Material.IRON_LEGGINGS).build()
        val ironBoots = ItemStack.builder(Material.IRON_BOOTS).build()
        val sword = ItemStack.builder(Material.DIAMOND_SWORD).build()
        use(KitsModule(showMenu = true,
            selectableKits = listOf(KitsModule.Kit(name = Component.text("Default", NamedTextColor.YELLOW),
                icon = Material.DIAMOND_CHESTPLATE,
                items = hashMapOf(0 to sword,
                    PlayerInventoryUtils.HELMET_SLOT to ironHelmet,
                    PlayerInventoryUtils.CHESTPLATE_SLOT to ironChestplate,
                    PlayerInventoryUtils.LEGGINGS_SLOT to ironLeggings,
                    PlayerInventoryUtils.BOOTS_SLOT to ironBoots)),
                KitsModule.Kit(name = Component.text("Hard Mode", NamedTextColor.RED),
                    icon = Material.REDSTONE,
                    items = hashMapOf(0 to sword, PlayerInventoryUtils.BOOTS_SLOT to ironBoots)))))

        ready()
    }
}