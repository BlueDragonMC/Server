package com.bluedragonmc.games.skyfall

import com.bluedragonmc.games.skyfall.module.SkyfallChickensModule
import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.PlayerKillPlayerEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.combat.ProjectileModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.ChestLootModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.*
import com.bluedragonmc.server.utils.noItalic
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.utils.inventory.PlayerInventoryUtils
import org.spongepowered.configurate.kotlin.extensions.get
import java.nio.file.Paths

/* Map data:
- additionalLocations[0]: Chickens
- additionalLocations[1]: Spawn Chests
- additionalLocations[2]: Supply Chests
- additionalLocations[3]: Mid Chests
 */
class SkyfallGame(mapName: String) : Game("Skyfall", mapName) {
    val config = use(ConfigModule("skyfall.yml")).getConfig()

    init {
        use(GuiModule())
        // Combat
        use(CustomDeathMessageModule())
        use(OldCombatModule())
        use(ProjectileModule())

        // Database
        use(AwardsModule())
        use(StatisticsModule())

        // Gameplay
        use(ChestLootModule(SkyfallLootProvider(this)))
        use(SidebarModule(name))

        // Instance
        use(SharedInstanceModule())

        // Map
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))

        // Minigame
        use(CountdownModule(threshold = 2, allowMoveDuringCountdown = false))
        use(
            KitsModule(
                selectableKits = listOf(
                    KitsModule.Kit(
                        name = "Default" withColor ALT_COLOR_1, items = hashMapOf(
                            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.of(
                                Material.ELYTRA
                            )
                        )
                    )
                )
            )
        )
        use(MOTDModule(motd = Component.translatable("game.skyfall.motd")))
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(SpawnpointModule(SpawnpointModule.DatabaseSpawnpointProvider()))
        use(SpectatorModule(spectateOnDeath = true))
        use(WinModule(winCondition = WinModule.WinCondition.LAST_PLAYER_ALIVE) { player, winningTeam ->
            if (winningTeam.players.contains(player)) 200
            else 20
        })

        // Vanilla
        use(ChestModule())
        use(FallDamageModule)
        use(FireworkRocketModule())
        use(ItemDropModule(dropBlocksOnBreak = true, dropAllOnDeath = true))
        use(ItemPickupModule())
        use(NaturalRegenerationModule())

        // Game-specific
        use(SkyfallChickensModule(mapData?.additionalLocations?.get(0) ?: listOf()))
        use(@DependsOn(StatisticsModule::class) object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(PlayerKillPlayerEvent::class.java) { event ->
                    getModule<StatisticsModule>().recordStatistic(
                        event.attacker,
                        "game_skyfall_kills"
                    ) { value -> value?.plus(1.0) ?: 1.0 }
                }
            }
        })

        ready()
    }

    class SkyfallLootProvider(private val game: Game) : ChestLootModule.ChestLootProvider {
        override fun getLoot(chestLocation: Point): Collection<ItemStack> {

            val chestLocation = Pos.fromPoint(chestLocation)
            val parentNode =
                if (game.mapData!!.additionalLocations[1].contains(chestLocation)) {
                    // Spawn chest
                    game.getModule<ConfigModule>().getConfig().node("loot", "spawn")
                } else if (game.mapData!!.additionalLocations[2].contains(chestLocation)) {
                    // Supply chest
                    game.getModule<ConfigModule>().getConfig().node("loot", "supply")
                } else if (game.mapData!!.additionalLocations[3].contains(chestLocation)) {
                    // Mid chest
                    game.getModule<ConfigModule>().getConfig().node("loot", "middle")
                } else {
                    // Chest was not configured correctly in the database
                    return listOf(
                        ItemStack.of(Material.RED_WOOL, 1).withDisplayName("Error" withColor NamedTextColor.RED)
                            .withLore(
                                mutableListOf(
                                    Component.text("This chest was not configured correctly.", NamedTextColor.GRAY)
                                        .noItalic(),
                                    Component.text("Please report this error on the forums.", NamedTextColor.GRAY)
                                        .noItalic()
                                )
                            )
                    )
                }
            val availableSlots = (0..26).toMutableList()
            val items = MutableList(27) { ItemStack.AIR }
            parentNode.childrenList().forEach { node ->
                val material = node.node("material").get<Material>()
                val chance = node.node("chance").double
                if (chance < Math.random()) return@forEach
                val quantityString = node.node("quantity").string!!
                val split = quantityString.split("-")

                val qtyRange = if (split.size == 1) split[0].toInt()..split[0].toInt()
                else split[0].toInt()..split[1].toInt()

                val itemStack =
                    if (material == null) node.node("item").get<ItemStack>()!!.withAmount(qtyRange.random())
                    else ItemStack.of(material, qtyRange.random())

                val slot = availableSlots.random()
                items[slot] = itemStack
                availableSlots.remove(slot)
            }

            return items
        }
    }

}