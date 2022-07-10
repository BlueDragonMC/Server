package com.bluedragonmc.server.game

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.MapData
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.InstanceContainerModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.CountdownModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.surroundWithSeparators
import com.bluedragonmc.server.utils.toPlainText
import kotlinx.coroutines.launch
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.InventoryType
import net.minestom.server.inventory.TransactionOption
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.utils.inventory.PlayerInventoryUtils
import java.nio.file.Paths
import java.time.Duration

class BedWarsGame(mapName: String) : Game("BedWars", mapName) {
    init {
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(InstanceContainerModule())
        use(VoidDeathModule(32.0))
        use(
            CountdownModule(
                2, true,
                OldCombatModule(allowDamage = true, allowKnockback = true),
                SpectatorModule(spectateOnDeath = false)
            )
        )
        use(WinModule(WinModule.WinCondition.LAST_TEAM_ALIVE) { player, winningTeam ->
            if (player in winningTeam.players) 500 else 50
        })
        use(SpawnpointModule(SpawnpointModule.TeamDatabaseSpawnpointProvider { ready() }))
        use(MOTDModule(Component.text("Collect resources at generators around the map.\n" + "When your team's bed is broken, you cannot respawn.")))
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(TeamModule(true, TeamModule.AutoTeamMode.PLAYER_COUNT, 1, teamsAutoAssignedCallback = {
            val spawnpointProvider = getModule<SpawnpointModule>().spawnpointProvider
            players.forEach {
                it.respawnPoint = spawnpointProvider.getSpawnpoint(it)
                it.teleport(it.respawnPoint)
            }
        }))
        use(ItemGeneratorsModule())
        use(AwardsModule())
        use(SidebarModule(name))
        use(NPCModule())
        use(GuiModule())
        use(ItemPickupModule())
        use(
            KitsModule(
                showMenu = true, giveKitsOnStart = true, selectableKits = listOf(
                    KitsModule.Kit(
                        Component.text("Armorer", NamedTextColor.YELLOW),
                        "When the game starts, receive the following items:\n- Wooden Sword\n- Iron Chestplate\n- Iron Leggings",
                        Material.IRON_CHESTPLATE,
                        items = hashMapOf(
                            0 to ItemStack.builder(Material.WOODEN_SWORD).build(),
                            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.builder(Material.IRON_CHESTPLATE).build(),
                            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.builder(Material.IRON_LEGGINGS).build()
                        )
                    ),
                    KitsModule.Kit(
                        Component.text("Swordsman", NamedTextColor.YELLOW),
                        "When the game starts, receive the following items:\n- Iron Sword\n- Leather Tunic\n- Leather Pants",
                        Material.IRON_SWORD,
                        items = hashMapOf(
                            0 to ItemStack.builder(Material.IRON_SWORD).build(),
                            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.builder(Material.LEATHER_CHESTPLATE)
                                .build(),
                            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.builder(Material.LEATHER_LEGGINGS).build()
                        )
                    ),
                    KitsModule.Kit(
                        Component.text("Builder", NamedTextColor.YELLOW),
                        "When the game starts, receive the following items:\n- Wooden Sword\n- 32 Wool\n- Leather Tunic\n- Leather Pants",
                        Material.WHITE_WOOL,
                        items = hashMapOf(
                            0 to ItemStack.builder(Material.WOODEN_SWORD).build(),
                            1 to ItemStack.builder(Material.WHITE_WOOL).amount(32).build(),
                            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.builder(Material.LEATHER_CHESTPLATE)
                                .build(),
                            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.builder(Material.LEATHER_LEGGINGS).build()
                        )
                    ),
                    KitsModule.Kit(
                        Component.text("Trader", NamedTextColor.YELLOW),
                        "When the game starts, receive the following items:\n- Wooden Sword\n- 32 Iron\n- Leather Tunic\n- Leather Pants",
                        Material.IRON_INGOT,
                        items = hashMapOf(
                            0 to ItemStack.builder(Material.WOODEN_SWORD).build(),
                            1 to ItemStack.builder(Material.IRON_INGOT).amount(32).build(),
                            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.builder(Material.LEATHER_CHESTPLATE)
                                .build(),
                            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.builder(Material.LEATHER_LEGGINGS).build()
                        )
                    )
                )
            )
        )
        use(object : GameModule() {
            private lateinit var mapData: MapData
            val playerPlacedBlocks = mutableListOf<Point>()

            override fun initialize(parent: Game, eventNode: EventNode<Event>) {

                val sidebar = parent.getModule<SidebarModule>()
                val teamModule = parent.getModule<TeamModule>()

                val sidebarTeamsSection = sidebar.bind {
                    teamModule.teams.map { t ->
                        "team-status-${t.name.toPlainText()}" to
                                (t.name + Component.text(":", NamedTextColor.GRAY) +
                                        (if(bedWarsTeamInfo[t]?.bedIntact != false) Component.text("✔", NamedTextColor.GREEN)
                        else Component.text("✖", NamedTextColor.RED)))
                    }
                }

                eventNode.addListener(GameStartEvent::class.java) { event ->
                    for (team in parent.getModule<TeamModule>().teams) {
                        bedWarsTeamInfo[team] = BedWarsTeamInfo(bedIntact = true)
                    }
                }

                // Without this it is possible to break your own bed, making you invincible
                eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
                    if (!getInstance().getBlock(event.blockPosition).isAir) event.isCancelled = true
                    playerPlacedBlocks.add(event.blockPosition)
                }

                eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
                    val team = bedBlockToTeam(event.block)
                    if (team != null) {
                        if (team == parent.getModule<TeamModule>().getTeam(event.player)) {
                            event.player.sendMessage(
                                Component.text("You cannot break your own bed!", NamedTextColor.RED)
                            )
                            event.isCancelled = true
                            return@addListener
                        }
                        if (!bedWarsTeamInfo.containsKey(team)) bedWarsTeamInfo[team] = BedWarsTeamInfo(false)
                        else bedWarsTeamInfo[team]!!.bedIntact = false
                        sidebar.updateBinding(sidebarTeamsSection)
                        sendMessage(
                            team.name.append(Component.text(" bed was broken by ", NamedTextColor.AQUA))
                                .append(event.player.name).surroundWithSeparators()
                        )
                        parent.playSound(Sound.sound(
                            Key.key("entity.enderdragon.growl"), Sound.Source.HOSTILE, 1.0f, 1.0f
                        ))
                        team.showTitle(Title.title(
                            Component.text("BED DESTROYED", NamedTextColor.RED, TextDecoration.BOLD),
                            Component.text("You can no longer respawn!", NamedTextColor.RED)
                        ))
                        return@addListener
                    }
                    if (playerPlacedBlocks.contains(event.blockPosition)) playerPlacedBlocks.remove(event.blockPosition)
                    else {
                        event.player.sendMessage(Component.text("You can only break blocks placed by a player!", NamedTextColor.RED))
                        event.isCancelled = true
                    }
                }

                eventNode.addListener(PlayerDeathEvent::class.java) { event ->
                    val team = parent.getModule<TeamModule>().getTeam(event.player)
                    if (!bedWarsTeamInfo[team]!!.bedIntact && !parent.getModule<SpectatorModule>()
                            .isSpectating(event.player)
                    )
                        parent.getModule<SpectatorModule>().addSpectator(event.player)

                    MinecraftServer.getSchedulerManager().buildTask {
                        if (parent.getModule<SpectatorModule>().isSpectating(event.player)) return@buildTask
                        event.player.respawn()
                        logger.info("Player ${event.player.username} respawned in BedWars")
                        event.player.gameMode = GameMode.SPECTATOR
                        MinecraftServer.getSchedulerManager().buildTask {
                            event.player.inventory.clear()
                            event.player.inventory.chestplate = ItemStack.of(Material.LEATHER_CHESTPLATE)
                            event.player.inventory.leggings = ItemStack.of(Material.LEATHER_LEGGINGS)
                            event.player.inventory.setItemStack(0, ItemStack.of(Material.WOODEN_SWORD))
                            event.player.gameMode = GameMode.SURVIVAL
                            event.player.teleport(event.player.respawnPoint)
                        }.delay(Duration.ofSeconds(5)).schedule()
                    }.delay(Duration.ofMillis(20)).schedule()
                }

                eventNode.addListener(GameStartEvent::class.java) { event ->
                    val generatorsModule = getModule<ItemGeneratorsModule>()
                    val spawnGenerators = mapData.additionalLocations[0]
                    val diamondGenerators = mapData.additionalLocations[1]
                    val emeraldGenerators = mapData.additionalLocations[2]
                    spawnGenerators.forEach {
                        generatorsModule.addGenerator(
                            ItemGeneratorsModule.ItemGenerator(
                                getInstance(), it, hashMapOf(
                                    ItemStack.of(Material.IRON_INGOT) to 1,
                                    ItemStack.of(Material.GOLD_INGOT) to 3,
                                )
                            )
                        )
                    }
                    diamondGenerators.forEach {
                        generatorsModule.addGenerator(
                            ItemGeneratorsModule.ItemGenerator(
                                getInstance(), it, hashMapOf(
                                    ItemStack.of(Material.DIAMOND) to 25,
                                )
                            )
                        )
                    }
                    emeraldGenerators.forEach {
                        generatorsModule.addGenerator(
                            ItemGeneratorsModule.ItemGenerator(
                                getInstance(), it, hashMapOf(
                                    ItemStack.of(Material.EMERALD) to 35,
                                )
                            )
                        )
                    }
                    val mainShopkeepers = mapData.additionalLocations[3]
                    val teamUpgradeShopkeepers = mapData.additionalLocations[4]
                    mainShopkeepers.forEach { pos ->
                        getModule<NPCModule>().addNPC(
                            instance = getInstance(),
                            position = pos,
                            customName = Component.text("Shop", NamedTextColor.YELLOW, TextDecoration.BOLD),
                            entityType = EntityType.VILLAGER,
                            interaction = {
                                openShop(it.player)
                            })
                    }
                    teamUpgradeShopkeepers.forEach { pos ->
                        getModule<NPCModule>().addNPC(
                            instance = getInstance(),
                            position = pos,
                            customName = Component.text("Upgrades", NamedTextColor.YELLOW, TextDecoration.BOLD),
                            entityType = EntityType.VILLAGER,
                            interaction = {
                                openTeamUpgradeShop(it.player)
                            })
                    }
                }

                DatabaseModule.IO.launch {
                    mapData = getModule<DatabaseModule>().getMap(mapName)
                }
            }
        })
    }

    fun buyItem(player: Player, item: ItemStack, price: Int, currency: Material) {
        val removeSuccess = player.inventory.takeItemStack(ItemStack.of(currency, price), TransactionOption.ALL_OR_NOTHING)
        val addSuccess = player.inventory.addItemStack(item, TransactionOption.DRY_RUN)
        if (!removeSuccess) {
            player.sendMessage(Component.text("You do not have enough ${currency.name()} to buy this item.", NamedTextColor.RED))
            return
        }
        if (!addSuccess) {
            player.sendMessage(Component.text("You do not have enough space in your inventory for this item.", NamedTextColor.RED))
            return
        }
        player.inventory.addItemStack(item, TransactionOption.ALL)
    }

    fun GuiModule.ItemsBuilder.slotShopItem(slotNumber: Int, item: ItemStack, price: Int, currency: Material) {
        slot(slotNumber, item.material(), { player ->
            displayName(Component.text(item.material().name(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("x${item.amount()}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
            lore(Component.text("Price: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$price ${currency.name()}", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)))
        }) {
            buyItem(this.player, item, price, currency)
        }
    }

    private val shop by lazy {
        getModule<GuiModule>().createMenu(Component.text("Shop"), InventoryType.CHEST_6_ROW, isPerPlayer = false) {
            slotShopItem(0, ItemStack.of(Material.WHITE_WOOL, 16), 10, Material.IRON_INGOT)
            slotShopItem(1, ItemStack.of(Material.OAK_WOOD, 8), 10, Material.GOLD_INGOT)
            slotShopItem(2, ItemStack.of(Material.END_STONE, 8), 50, Material.IRON_INGOT)

            slotShopItem(pos(2, 1), ItemStack.of(Material.SHEARS, 1), 50, Material.IRON_INGOT)
            slotShopItem(pos(2, 2), ItemStack.of(Material.STONE_PICKAXE, 1), 50, Material.IRON_INGOT)
            slotShopItem(pos(2, 3), ItemStack.of(Material.STONE_AXE, 1), 50, Material.IRON_INGOT)

            slotShopItem(pos(3, 1), ItemStack.of(Material.IRON_SWORD, 1), 10, Material.GOLD_INGOT)
            val stickItem = ItemStack.builder(Material.STICK).displayName(Component.text("Knockback Stick"))
                .lore(Component.text("Use this to wack your enemies"), Component.text("off the map!"))
                .meta { metaBuilder: ItemMeta.Builder ->
                    metaBuilder.enchantment(Enchantment.KNOCKBACK, 10)
                }.build()
            slotShopItem(pos(3, 2), stickItem, 3, Material.EMERALD)
        }
    }

    fun openShop(player: Player) = shop.open(player)

    fun openTeamUpgradeShop(player: Player) {

    }

    fun bedBlockToTeam(bed: Block): TeamModule.Team? {
        return when (bed.registry().material()) {
            Material.RED_BED -> {
                getModule<TeamModule>().getTeam(NamedTextColor.RED)
            }
            Material.BLUE_BED -> {
                getModule<TeamModule>().getTeam(NamedTextColor.BLUE)
            }
            Material.GREEN_BED -> {
                getModule<TeamModule>().getTeam(NamedTextColor.GREEN)
            }
            Material.CYAN_BED -> {
                getModule<TeamModule>().getTeam(NamedTextColor.AQUA)
            }
            Material.PINK_BED -> {
                getModule<TeamModule>().getTeam(NamedTextColor.LIGHT_PURPLE)
            }
            Material.WHITE_BED -> {
                getModule<TeamModule>().getTeam(NamedTextColor.WHITE)
            }
            Material.GRAY_BED -> {
                getModule<TeamModule>().getTeam(NamedTextColor.GRAY)
            }
            Material.YELLOW_BED -> {
                getModule<TeamModule>().getTeam(NamedTextColor.YELLOW)
            }
            Material.ORANGE_BED -> {
                getModule<TeamModule>().getTeam(NamedTextColor.GOLD)
            }
            Material.PURPLE_BED -> {
                getModule<TeamModule>().getTeam(NamedTextColor.DARK_PURPLE)
            }
            else -> {
                null
            }
        }
    }

    /*
    Team info includes:
    - List of team upgrades
    - Bed status
     */
    val bedWarsTeamInfo = hashMapOf<TeamModule.Team, BedWarsTeamInfo>()

    inner class BedWarsTeamInfo(var bedIntact: Boolean = true)
}