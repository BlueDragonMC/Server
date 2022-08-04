package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.InstanceContainerModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.noItalic
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemMeta
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
        use(InstanceContainerModule())
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
        use(CustomDeathMessageModule())
        use(ItemPickupModule())
        use(WorldPermissionsModule(allowBlockBreak = true, allowBlockPlace = true, allowBlockInteract = true))
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(TeamModule(autoTeams = true, autoTeamMode = TeamModule.AutoTeamMode.PLAYER_COUNT, autoTeamCount = 1, teamsAutoAssignedCallback = {
            val spawnpointProvider = getModule<SpawnpointModule>().spawnpointProvider
            players.forEach {
                it.respawnPoint = spawnpointProvider.getSpawnpoint(it)
                it.teleport(it.respawnPoint)
            }
        }))
        use(SpawnpointModule(SpawnpointModule.TeamDatabaseSpawnpointProvider(allowRandomOrder = true)))
        use(FallDamageModule)
        use(NaturalRegenerationModule())
        use(InventoryPermissionsModule(allowDropItem = true, allowMoveItem = true))
        use(AwardsModule())

        use(GuiModule())
        use(ChestLootModule(NormalSkyWarsLootProvider(this)))

        ready()
    }

    /**
     * A [ChestLootModule.ChestLootProvider] that can differentiate between spawn and mid chests
     * using the map data stored in the database.
     */
    abstract class SkyWarsLootProvider(private val game: Game) : ChestLootModule.ChestLootProvider {

        override fun getLoot(chestLocation: Point): Collection<ItemStack> {
            if (game.mapData!!.additionalLocations[0].contains(Pos(chestLocation.x(), chestLocation.y(), chestLocation.z()))) {
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
            loot += ItemStack.of(Material.WHITE_WOOL, (12 .. 24).random())
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

        private val stickItem = ItemStack.builder(Material.STICK).displayName(Component.text("Knockback Stick"))
            .lore(
                Component.text("Use this to wack your enemies", NamedTextColor.GRAY).noItalic(),
                Component.text("off the map!", NamedTextColor.GRAY).noItalic()
            )
            .meta { metaBuilder: ItemMeta.Builder ->
                metaBuilder.enchantment(Enchantment.KNOCKBACK, 2)
            }.build()

        override fun getMidLoot(): Collection<ItemStack> {
            val loot = arrayListOf<ItemStack>()

            loot += ItemStack.of(Material.WHITE_WOOL, (8 .. 16).random())
            loot += ItemStack.of(Material.GOLDEN_APPLE, (0 .. 2).random())

            loot += ItemStack.of(Material.IRON_SWORD, (0 .. 1).random())
            loot += ItemStack.of(Material.DIAMOND_SWORD, (0 .. 1).random())

            if ((0 .. 4).random() == 0) loot += ItemStack.of(Material.IRON_HELMET)
            if ((0 .. 4).random() == 0) loot += ItemStack.of(Material.IRON_CHESTPLATE)
            if ((0 .. 4).random() == 0) loot += ItemStack.of(Material.IRON_LEGGINGS)
            if ((0 .. 4).random() == 0) loot += ItemStack.of(Material.IRON_BOOTS)

            if ((0 .. 7).random() == 0) loot += ItemStack.of(Material.DIAMOND_HELMET)
            if ((0 .. 7).random() == 0) loot += ItemStack.of(Material.DIAMOND_CHESTPLATE)
            if ((0 .. 7).random() == 0) loot += ItemStack.of(Material.DIAMOND_LEGGINGS)
            if ((0 .. 7).random() == 0) loot += ItemStack.of(Material.DIAMOND_BOOTS)

            if ((0 .. 5).random() == 0) loot += stickItem

            return loot
        }

    }
}