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
        use(MOTDModule(Component.translatable("game.skywars.motd")))
        use(InstantRespawnModule())
        use(ItemDropModule(dropBlocksOnBreak = true, dropAllOnDeath = true))
        use(CustomDeathMessageModule())
        use(ItemPickupModule())
        use(WorldPermissionsModule(allowBlockBreak = true, allowBlockPlace = true, allowBlockInteract = true))
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(TeamModule(autoTeams = true,
            autoTeamMode = TeamModule.AutoTeamMode.PLAYER_COUNT,
            autoTeamCount = 1,
            teamsAutoAssignedCallback = {
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

    data class SkyWarsLootItem(val item: ItemStack, val chance: Double, val quantity: IntRange) {
        constructor(item: ItemStack, chance: Double, quantity: Int) : this(item, chance, quantity..quantity)
        constructor(item: Material, chance: Double, quantity: IntRange) : this(ItemStack.of(item), chance, quantity)
        constructor(item: Material, chance: Double, quantity: Int) : this(ItemStack.of(item),
            chance,
            quantity..quantity)
    }


    /**
     * A [ChestLootModule.ChestLootProvider] that can differentiate between spawn and middle chests
     * using the map data stored in the database.
     */
    abstract class SkyWarsLootProvider(private val game: Game) : ChestLootModule.ChestLootProvider {

        override fun getLoot(chestLocation: Point): Collection<ItemStack> {
            val lootItems = getLootItems(chestLocation)
            return lootItems.filter {
                Math.random() < it.chance
            }.map {
                it.item.withAmount(it.quantity.random())
            }
        }

        private fun getLootItems(chestLocation: Point): Collection<SkyWarsLootItem> {
            if (game.mapData!!.additionalLocations[0].contains(chestLocation)
            ) {
                return getMidLoot()
            }
            return getSpawnLoot()
        }

        abstract fun getSpawnLoot(): Collection<SkyWarsLootItem>

        abstract fun getMidLoot(): Collection<SkyWarsLootItem>
    }

    class NormalSkyWarsLootProvider(game: Game) : SkyWarsLootProvider(game) {

        private val stickItem = ItemStack.builder(Material.STICK).displayName(Component.text("Knockback Stick"))
            .lore(
                Component.text("Use this to wack your enemies", NamedTextColor.GRAY).noItalic(),
                Component.text("off the map!", NamedTextColor.GRAY).noItalic()
            )
            .meta { metaBuilder: ItemMeta.Builder ->
                metaBuilder.enchantment(Enchantment.KNOCKBACK, 2)
            }.build()

        override fun getSpawnLoot() = listOf(
            SkyWarsLootItem(Material.WHITE_WOOL, 1.0, 12..36),
            SkyWarsLootItem(Material.COOKED_BEEF, 0.5, 1..3),
            SkyWarsLootItem(Material.STONE_SWORD, 0.5, 1),
            SkyWarsLootItem(Material.IRON_SWORD, 0.5, 1),

            SkyWarsLootItem(Material.LEATHER_HELMET, 0.15, 1),
            SkyWarsLootItem(Material.LEATHER_CHESTPLATE, 0.15, 1),
            SkyWarsLootItem(Material.LEATHER_LEGGINGS, 0.15, 1),
            SkyWarsLootItem(Material.LEATHER_BOOTS, 0.15, 1),

            SkyWarsLootItem(Material.CHAINMAIL_HELMET, 0.10, 1),
            SkyWarsLootItem(Material.CHAINMAIL_CHESTPLATE, 0.10, 1),
            SkyWarsLootItem(Material.CHAINMAIL_LEGGINGS, 0.10, 1),
            SkyWarsLootItem(Material.CHAINMAIL_BOOTS, 0.10, 1),

            SkyWarsLootItem(Material.IRON_HELMET, 0.05, 1),
            SkyWarsLootItem(Material.IRON_CHESTPLATE, 0.05, 1),
            SkyWarsLootItem(Material.IRON_LEGGINGS, 0.05, 1),
            SkyWarsLootItem(Material.IRON_BOOTS, 0.05, 1),
        )

        override fun getMidLoot() = listOf(
            SkyWarsLootItem(Material.WHITE_WOOL, 1.0, 8..16),
            SkyWarsLootItem(Material.GOLDEN_APPLE, 1.0, 0..3),
            SkyWarsLootItem(Material.IRON_SWORD, 0.5, 1),
            SkyWarsLootItem(Material.DIAMOND_SWORD, 0.5, 1),
            SkyWarsLootItem(Material.FLINT_AND_STEEL, 0.4, 1),

            SkyWarsLootItem(Material.IRON_HELMET, 0.20, 1),
            SkyWarsLootItem(Material.IRON_CHESTPLATE, 0.20, 1),
            SkyWarsLootItem(Material.IRON_LEGGINGS, 0.20, 1),
            SkyWarsLootItem(Material.IRON_BOOTS, 0.20, 1),

            SkyWarsLootItem(Material.DIAMOND_HELMET, 0.125, 1),
            SkyWarsLootItem(Material.DIAMOND_CHESTPLATE, 0.125, 1),
            SkyWarsLootItem(Material.DIAMOND_LEGGINGS, 0.125, 1),
            SkyWarsLootItem(Material.DIAMOND_BOOTS, 0.125, 1),

            SkyWarsLootItem(Material.NETHERITE_HELMET, 0.01, 1),
            SkyWarsLootItem(Material.NETHERITE_CHESTPLATE, 0.01, 1),
            SkyWarsLootItem(Material.NETHERITE_LEGGINGS, 0.01, 1),
            SkyWarsLootItem(Material.NETHERITE_BOOTS, 0.01, 1),

            SkyWarsLootItem(stickItem, 0.16, 1)
        )
    }
}