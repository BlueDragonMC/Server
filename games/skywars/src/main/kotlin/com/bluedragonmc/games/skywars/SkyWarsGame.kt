package com.bluedragonmc.games.skywars

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.combat.ProjectileModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.ChestLootModule
import com.bluedragonmc.server.module.gameplay.InstantRespawnModule
import com.bluedragonmc.server.module.gameplay.InventoryPermissionsModule
import com.bluedragonmc.server.module.instance.InstanceContainerModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.*
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.GameMode
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import java.nio.file.Paths

/**
 * Each team spawns on their own island.
 * Collect items from your chests, and collect better
 * items at the middle island. Last team standing wins!
 * In the map database, `additionalLocations[0]` is a list of all middle chests. All other chests are considered spawn chests.
 */
class SkyWarsGame(mapName: String) : Game("SkyWars", mapName) {
    init {

        val config = use(ConfigModule("skywars.yml")).getConfig()

        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(InstanceContainerModule())
        use(VoidDeathModule(32.0))
        use(
            CountdownModule(2, true,
                OldCombatModule(allowDamage = true, allowKnockback = true),
                SpectatorModule(spectateOnDeath = true),
                ChestModule(),
                NaturalRegenerationModule(),
                ChestLootModule(NormalSkyWarsLootProvider(config, this))
            )
        )
        use(ProjectileModule())
        use(WinModule(WinModule.WinCondition.LAST_TEAM_ALIVE) { player, winningTeam ->
            if (player in winningTeam.players) 250 else 25
        })
        use(MOTDModule(Component.translatable("game.skywars.motd")))
        use(InstantRespawnModule())
        use(ItemDropModule(dropBlocksOnBreak = true, dropAllOnDeath = true))
        use(CustomDeathMessageModule())
        use(ItemPickupModule())
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
        use(InventoryPermissionsModule(allowDropItem = true, allowMoveItem = true))
        use(AwardsModule())

        use(GuiModule())

        use(StatisticsModule())

        ready()
    }

    data class SkyWarsLootItem(val item: ItemStack, val chance: Double, val quantity: IntRange) {
        constructor(item: Material, chance: Double, quantity: IntRange) : this(ItemStack.of(item), chance, quantity)
    }


    /**
     * A [ChestLootModule.ChestLootProvider] that can differentiate between spawn and middle chests
     * using the map data stored in the database.
     */
    abstract class SkyWarsLootProvider(private val game: Game) : ChestLootModule.ChestLootProvider {

        override fun getLoot(chestLocation: Point): Collection<ItemStack> {
            val availableSlots = (0..26).toMutableList()
            val items = MutableList(27) { ItemStack.AIR }
            val lootItems = getLootItems(chestLocation)
            lootItems.filter {
                Math.random() < it.chance
            }.forEach {
                val slot = availableSlots.random()
                availableSlots.remove(slot)
                items[slot] = it.item.withAmount(it.quantity.random())
            }
            return items
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

    class NormalSkyWarsLootProvider(private val config: ConfigurationNode, game: Game) : SkyWarsLootProvider(game) {

        private fun getLoot(category: String): Collection<SkyWarsLootItem> {
            val parentNode = config.node("loot", category)
            return parentNode.childrenList().map { node ->
                val material = node.node("material").get<Material>()
                val chance = node.node("chance").double
                val quantityString = node.node("quantity").string!!
                val split = quantityString.split("-")

                val qtyRange = if (split.size == 1) split[0].toInt()..split[0].toInt()
                else split[0].toInt()..split[1].toInt()

                if (material == null) {
                    val itemStack = node.node("item").get<ItemStack>()!!
                    SkyWarsLootItem(itemStack, chance, qtyRange)
                } else {
                    SkyWarsLootItem(material, chance, qtyRange)
                }
            }
        }

        override fun getSpawnLoot() = getLoot("spawn")
        override fun getMidLoot() = getLoot("middle")
    }
}