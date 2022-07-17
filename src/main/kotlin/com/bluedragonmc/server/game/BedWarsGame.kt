package com.bluedragonmc.server.game

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
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
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.attribute.Attribute
import net.minestom.server.attribute.AttributeModifier
import net.minestom.server.attribute.AttributeOperation
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemMeta
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.sound.SoundEvent
import net.minestom.server.utils.inventory.PlayerInventoryUtils
import java.nio.file.Paths
import java.util.*


class BedWarsGame(mapName: String) : Game("BedWars", mapName) {

    val otherTeamBedDestroyedSound = Sound.sound(SoundEvent.ENTITY_ENDER_DRAGON_DEATH, Sound.Source.HOSTILE, 1.0f, 1.0f)
    val bedDestroyedSound = Sound.sound(SoundEvent.ENTITY_WITHER_DEATH, Sound.Source.HOSTILE, 1.0f, 1.0f)

    init {
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(InstanceContainerModule())
        use(VoidDeathModule(32.0))
        use(
            CountdownModule(
                2,
                true,
                OldCombatModule(allowDamage = true, allowKnockback = true),
                SpectatorModule(spectateOnDeath = false),
                TimedRespawnModule(seconds = 5)
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
        use(ShopModule())
        use(ItemPickupModule())
        use(ChestModule())
        use(NaturalRegenerationModule())
        use(
            WorldPermissionsModule(
                allowBlockBreak = true, allowBlockPlace = true, allowBlockInteract = true, allowBreakMap = false
            )
        )
        use(ItemDropModule(dropAllOnDeath = true))
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
                    ), KitsModule.Kit(
                        Component.text("Swordsman", NamedTextColor.YELLOW),
                        "When the game starts, receive the following items:\n- Diamond Sword\n- Leather Tunic\n- Leather Pants",
                        Material.DIAMOND_SWORD,
                        items = hashMapOf(
                            0 to ItemStack.builder(Material.DIAMOND_SWORD).build(),
                            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.builder(Material.LEATHER_CHESTPLATE)
                                .build(),
                            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.builder(Material.LEATHER_LEGGINGS).build()
                        )
                    ), KitsModule.Kit(
                        Component.text("Builder", NamedTextColor.YELLOW),
                        "When the game starts, receive the following items:\n- Wooden Sword\n- 48 Wool\n- Leather Tunic\n- Leather Pants",
                        Material.WHITE_WOOL,
                        items = hashMapOf(
                            0 to ItemStack.builder(Material.WOODEN_SWORD).build(),
                            1 to ItemStack.builder(Material.WHITE_WOOL).amount(48).build(),
                            PlayerInventoryUtils.CHESTPLATE_SLOT to ItemStack.builder(Material.LEATHER_CHESTPLATE)
                                .build(),
                            PlayerInventoryUtils.LEGGINGS_SLOT to ItemStack.builder(Material.LEATHER_LEGGINGS).build()
                        )
                    ), KitsModule.Kit(
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

            override fun initialize(parent: Game, eventNode: EventNode<Event>) {

                val sidebar = parent.getModule<SidebarModule>()
                val teamModule = parent.getModule<TeamModule>()

                lateinit var sidebarTeamsSection: SidebarModule.ScoreboardBinding

                eventNode.addListener(GameStartEvent::class.java) {
                    val spectatorModule = parent.getModule<SpectatorModule>()
                    sidebarTeamsSection = sidebar.bind {
                        teamModule.teams.map { t ->
                            "team-status-${t.name.toPlainText()}" to (t.name + Component.text(
                                ": ", NamedTextColor.GRAY
                            ) + (if (bedWarsTeamInfo[t]?.bedIntact != false) Component.text(
                                "✔", NamedTextColor.GREEN
                            )
                            else Component.text(
                                t.players.count { !spectatorModule.isSpectating(it) }, NamedTextColor.RED
                            )))
                        }
                    }
                    for (team in parent.getModule<TeamModule>().teams) {
                        bedWarsTeamInfo[team] = BedWarsTeamInfo(bedIntact = true)
                    }
                    sidebarTeamsSection.update()
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
                        sidebarTeamsSection.update()
                        sendMessage((
                            team.name + Component.text(
                                " bed was broken by ", BRAND_COLOR_PRIMARY_2
                            ) + event.player.name).surroundWithSeparators()
                        )
                        for (player in parent.players) {
                            if (!team.players.contains(player)) {
                                player.playSound(otherTeamBedDestroyedSound)
                            } else {
                                player.showTitle(
                                    Title.title(
                                        Component.text("BED DESTROYED", NamedTextColor.RED, TextDecoration.BOLD),
                                        Component.text("You can no longer respawn!", NamedTextColor.RED)
                                    )
                                )
                                player.playSound(bedDestroyedSound)
                            }
                        }

                        // Break both parts of the bed
                        var facing = BlockFace.valueOf(event.block.getProperty("facing").uppercase(Locale.getDefault()))
                        if (event.block.getProperty("part") == "head") facing = facing.oppositeFace
                        event.instance.setBlock(event.blockPosition.relative(facing), Block.AIR)

                        return@addListener
                    }
                }

                eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
                    val team = parent.getModule<TeamModule>().getTeam(event.player) ?: return@addListener
                    if (event.block.registry().material() == Material.WHITE_WOOL) event.block =
                        teamToWoolBlock[team.name.color()] ?: Block.WHITE_WOOL
                }

                eventNode.addListener(PlayerDeathEvent::class.java) { event ->
                    val team = parent.getModule<TeamModule>().getTeam(event.player)
                    if (!bedWarsTeamInfo[team]!!.bedIntact && !parent.getModule<SpectatorModule>()
                            .isSpectating(event.player)
                    ) {
                        parent.getModule<SpectatorModule>().addSpectator(event.player)
                        sidebarTeamsSection.update()
                    }
                }

                eventNode.addListener(TimedRespawnModule.TimedRespawnEvent::class.java) { event ->
                    event.player.inventory.clear()
                    event.player.inventory.chestplate = ItemStack.of(Material.LEATHER_CHESTPLATE)
                    event.player.inventory.leggings = ItemStack.of(Material.LEATHER_LEGGINGS)
                    event.player.inventory.setItemStack(0, ItemStack.of(Material.WOODEN_SWORD))

                    // Re-add team upgrades every time the player respawns
                    // because status effects and other modifications are cleared on death
                    (event.player as CustomPlayer).virtualItems.filterIsInstance<ShopModule.TeamUpgrade>()
                        .forEach { upgrade ->
                            upgrade.baseObtainedCallback(event.player, upgrade)
                        }
                }

                eventNode.addListener(GameStartEvent::class.java) {
                    val generatorsModule = getModule<ItemGeneratorsModule>()
                    val (spawnGenerators, diamondGenerators, emeraldGenerators, mainShopkeepers, teamUpgradeShopkeepers) = mapData.additionalLocations
                    generatorsModule.addGenerator(
                        getInstance(), spawnGenerators, mapOf(
                            ItemStack.of(Material.IRON_INGOT) to 1,
                            ItemStack.of(Material.GOLD_INGOT) to 3,
                        )
                    )
                    generatorsModule.addGenerator(
                        getInstance(), diamondGenerators, mapOf(
                            ItemStack.of(Material.DIAMOND) to 25,
                        )
                    )
                    generatorsModule.addGenerator(
                        getInstance(), emeraldGenerators, mapOf(
                            ItemStack.of(Material.EMERALD) to 35,
                        )
                    )

                    getModule<NPCModule>().addNPC(instance = getInstance(),
                        positions = mainShopkeepers,
                        customName = Component.text("Shop", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        skin = NPCModule.NPCSkins.WEIRD_FARMER.skin,
                        entityType = EntityType.PLAYER,
                        interaction = {
                            openShop(it.player)
                        })

                    getModule<NPCModule>().addNPC(instance = getInstance(),
                        positions = teamUpgradeShopkeepers,
                        customName = Component.text("Upgrades", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        skin = NPCModule.NPCSkins.WEIRD_FARMER.skin,
                        entityType = EntityType.PLAYER,
                        interaction = {
                            openUpgradesMenu(it.player)
                        })
                }

                eventNode.addListener(WorldPermissionsModule.PreventPlayerBreakMapEvent::class.java) { event ->
                    event.isCancelled = bedBlockToTeam.containsKey(event.block.registry().material())
                }

                DatabaseModule.IO.launch {
                    mapData = getModule<DatabaseModule>().getMap(mapName)
                }
            }
        })
    }

    private val shop by lazy {
        getModule<ShopModule>().createShop("Shop") {
            item(1, 1, Material.WHITE_WOOL, 16, 10, Material.IRON_INGOT)
            item(1, 2, Material.OAK_WOOD, 8, 10, Material.GOLD_INGOT)
            item(1, 3, Material.END_STONE, 8, 50, Material.IRON_INGOT)

            item(2, 1, Material.SHEARS, 1, 50, Material.IRON_INGOT)
            item(2, 2, Material.STONE_PICKAXE, 1, 50, Material.IRON_INGOT)
            item(2, 3, Material.STONE_AXE, 1, 50, Material.IRON_INGOT)
            item(2, 4, Material.IRON_PICKAXE, 1, 20, Material.GOLD_INGOT)
            item(2, 5, Material.IRON_AXE, 1, 20, Material.GOLD_INGOT)

            item(3, 1, Material.STONE_SWORD, 1, 15, Material.IRON_INGOT)
            item(3, 2, Material.IRON_SWORD, 1, 10, Material.GOLD_INGOT)
            item(3, 3, Material.DIAMOND_SWORD, 1, 5, Material.EMERALD)

            item(4, 1, Material.IRON_HELMET, 1, 20, Material.GOLD_INGOT)
            item(4, 2, Material.IRON_CHESTPLATE, 1, 5, Material.EMERALD)
            item(4, 3, Material.IRON_LEGGINGS, 1, 5, Material.EMERALD)
            item(4, 4, Material.IRON_BOOTS, 1, 20, Material.GOLD_INGOT)
            val stickItem = ItemStack.builder(Material.STICK).displayName(Component.text("Knockback Stick"))
                .lore(Component.text("Use this to wack your enemies"), Component.text("off the map!"))
                .meta { metaBuilder: ItemMeta.Builder ->
                    metaBuilder.enchantment(Enchantment.KNOCKBACK, 3)
                }.build()
            item(3, 4, stickItem, 3, Material.EMERALD)
        }
    }

    private val teamToWoolBlock = mapOf(
        NamedTextColor.RED to Block.RED_WOOL,
        NamedTextColor.BLUE to Block.BLUE_WOOL,
        NamedTextColor.GREEN to Block.GREEN_WOOL,
        NamedTextColor.AQUA to Block.CYAN_WOOL,
        NamedTextColor.LIGHT_PURPLE to Block.PINK_WOOL,
        NamedTextColor.WHITE to Block.WHITE_WOOL,
        NamedTextColor.GRAY to Block.GRAY_WOOL,
        NamedTextColor.YELLOW to Block.YELLOW_WOOL,
        NamedTextColor.GOLD to Block.ORANGE_WOOL,
        NamedTextColor.DARK_PURPLE to Block.PURPLE_WOOL
    )

    // There's no way we're keeping these names
    // Some team upgrades need to be registered, so they reapply when you respawn (look in the TimedRespawnEvent handler above)
    private val fastFeet = ShopModule.TeamUpgrade(
        "Fast Feet", "Gives Speed I to all members on your team.", Material.IRON_BOOTS
    ) { player, _ ->
        player.getAttribute(Attribute.MOVEMENT_SPEED)
            .addModifier(AttributeModifier("bluedragon:fastfeet", 1.1f, AttributeOperation.MULTIPLY_BASE))
    }

    private val miningMalarkey = ShopModule.TeamUpgrade(
        "Mining Malarkey", "Gives Haste I to all members on your team.", Material.IRON_PICKAXE
    ) { player, _ -> player.addEffect(Potion(PotionEffect.HASTE, 1, Integer.MAX_VALUE, Potion.ICON_FLAG)) }

    private val upgrades by lazy {
        getModule<ShopModule>().createShop("Team Upgrades") {
            teamUpgrade(1, 1, 3, Material.DIAMOND, fastFeet)
            teamUpgrade(1, 2, 5, Material.DIAMOND, miningMalarkey)
        }
    }

    fun openShop(player: Player) = shop.open(player)
    fun openUpgradesMenu(player: Player) = upgrades.open(player)

    private val bedBlockToTeam = mapOf(
        Material.RED_BED to NamedTextColor.RED,
        Material.BLUE_BED to NamedTextColor.BLUE,
        Material.GREEN_BED to NamedTextColor.GREEN,
        Material.CYAN_BED to NamedTextColor.AQUA,
        Material.PINK_BED to NamedTextColor.LIGHT_PURPLE,
        Material.WHITE_BED to NamedTextColor.WHITE,
        Material.GRAY_BED to NamedTextColor.GRAY,
        Material.YELLOW_BED to NamedTextColor.YELLOW,
        Material.ORANGE_BED to NamedTextColor.GOLD,
        Material.PURPLE_BED to NamedTextColor.DARK_PURPLE,
    )

    fun bedBlockToTeam(bed: Block): TeamModule.Team? {
        return getModule<TeamModule>().getTeam(bedBlockToTeam[bed.registry().material()] ?: return null)
    }

    /*
    Team info includes:
    - List of team upgrades
    - Bed status
     */
    val bedWarsTeamInfo = hashMapOf<TeamModule.Team, BedWarsTeamInfo>()

    inner class BedWarsTeamInfo(var bedIntact: Boolean = true)

}