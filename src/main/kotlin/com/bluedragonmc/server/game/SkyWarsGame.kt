package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.MapData
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.module.minigame.WinModule
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.nio.file.Paths

/**
 * Each team spawns on their own island.
 * Collect items from your chests, and collect better
 * items at the middle island. Last team standing wins!
 * In the map database, `additionalLocations[0]` is a list of all middle chests. All other chests are considered spawn chests.
 */
class SkyWarsGame(mapName: String) : Game("SkyWars", mapName) {
    init {
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(SharedInstanceModule())
        use(VoidDeathModule(32.0))
        use(
            CountdownModule(2, true,
            OldCombatModule(allowDamage = true, allowKnockback = true),
            SpectatorModule(spectateOnDeath = true),
            ChestModule()
            )
        )
        use(WinModule(WinModule.WinCondition.LAST_TEAM_ALIVE) { player, winningTeam ->
            if (player in winningTeam.players) 250 else 25
        })
        use(MOTDModule(Component.text("Each team spawns on their own island.\n" + "Collect items from your chests, and collect better\n" + "items at the middle island. Last team standing wins!")))
        use(InstantRespawnModule())
        use(ItemDropModule(dropBlocksOnBreak = true, dropAllOnDeath = true))
        use(ItemPickupModule())
        use(WorldPermissionsModule(allowBlockBreak = true, allowBlockPlace = true, allowBlockInteract = true))
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(SpawnpointModule(SpawnpointModule.TeamDatabaseSpawnpointProvider(allowRandomOrder = true, callback = { ready() })))
        use(FallDamageModule)
        use(NaturalRegenerationModule())
        use(InventoryPermissionsModule(allowDropItem = true, allowMoveItem = true, forcedItemSlot = null))
        use(TeamModule(autoTeams = true, autoTeamMode = TeamModule.AutoTeamMode.PLAYER_COUNT, autoTeamCount = 1, teamsAutoAssignedCallback = {
            val spawnpointProvider = getModule<SpawnpointModule>().spawnpointProvider
            players.forEach {
                it.respawnPoint = spawnpointProvider.getSpawnpoint(it)
                it.teleport(it.respawnPoint)
            }
        }))
        use(AwardsModule())

        use(GuiModule())
        use(ChestLootModule(NormalSkyWarsLootProvider(this)))
    }

    /**
     * A [ChestLootModule.ChestLootProvider] that can differentiate between spawn and mid chests
     * using the map data stored in the database.
     */
    abstract class SkyWarsLootProvider(game: Game) : ChestLootModule.ChestLootProvider {
        private lateinit var mapData: MapData

        init {
            DatabaseModule.IO.launch {
                mapData = game.getModule<DatabaseModule>().getMap(game.mapName)
            }
        }

        override fun getLoot(chestLocation: Point): Collection<ItemStack> {
            if (mapData.additionalLocations[0].contains(Pos(chestLocation.x(), chestLocation.y(), chestLocation.z()))) {
                return getMidLoot()
            }
            return getSpawnLoot()
        }

        abstract fun getSpawnLoot(): Collection<ItemStack>

        abstract fun getMidLoot(): Collection<ItemStack>
    }

    class NormalSkyWarsLootProvider(game: Game) : SkyWarsLootProvider(game) {
        override fun getSpawnLoot(): Collection<ItemStack> {
            val loot = arrayListOf<ItemStack>()
            loot += ItemStack.of(Material.WHITE_WOOL, (4 .. 8).random())
            loot += ItemStack.of(Material.COOKED_BEEF, (0 .. 1).random())

            loot += ItemStack.of(Material.STONE_SWORD, (0 .. 1).random())
            loot += ItemStack.of(Material.IRON_SWORD, (0 .. 1).random())

            if ((0 .. 6).random() == 0) loot += ItemStack.of(Material.LEATHER_HELMET)
            if ((0 .. 6).random() == 0) loot += ItemStack.of(Material.LEATHER_CHESTPLATE)
            if ((0 .. 6).random() == 0) loot += ItemStack.of(Material.LEATHER_LEGGINGS)
            if ((0 .. 6).random() == 0) loot += ItemStack.of(Material.LEATHER_BOOTS)

            if ((0 .. 10).random() == 0) loot += ItemStack.of(Material.CHAINMAIL_HELMET)
            if ((0 .. 10).random() == 0) loot += ItemStack.of(Material.CHAINMAIL_CHESTPLATE)
            if ((0 .. 10).random() == 0) loot += ItemStack.of(Material.CHAINMAIL_LEGGINGS)
            if ((0 .. 10).random() == 0) loot += ItemStack.of(Material.CHAINMAIL_BOOTS)

            return loot
        }

        override fun getMidLoot(): Collection<ItemStack> {
            val loot = arrayListOf<ItemStack>()

            loot += ItemStack.of(Material.WHITE_WOOL, (8 .. 16).random())
            loot += ItemStack.of(Material.GOLDEN_APPLE, (0 .. 1).random())

            loot += ItemStack.of(Material.IRON_SWORD, (0 .. 1).random())
            loot += ItemStack.of(Material.DIAMOND_SWORD, (0 .. 1).random())

            if ((0 .. 6).random() == 0) loot += ItemStack.of(Material.CHAINMAIL_HELMET)
            if ((0 .. 6).random() == 0) loot += ItemStack.of(Material.CHAINMAIL_CHESTPLATE)
            if ((0 .. 6).random() == 0) loot += ItemStack.of(Material.CHAINMAIL_LEGGINGS)
            if ((0 .. 6).random() == 0) loot += ItemStack.of(Material.CHAINMAIL_BOOTS)

            if ((0 .. 10).random() == 0) loot += ItemStack.of(Material.IRON_HELMET)
            if ((0 .. 10).random() == 0) loot += ItemStack.of(Material.IRON_CHESTPLATE)
            if ((0 .. 10).random() == 0) loot += ItemStack.of(Material.IRON_LEGGINGS)
            if ((0 .. 10).random() == 0) loot += ItemStack.of(Material.IRON_BOOTS)

            return loot
        }

    }
}