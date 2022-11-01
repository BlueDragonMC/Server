package com.bluedragonmc.games.bedwars

import com.bluedragonmc.games.bedwars.module.ItemGeneratorsModule
import com.bluedragonmc.games.bedwars.upgrades.FastFeetTeamUpgrade
import com.bluedragonmc.games.bedwars.upgrades.MiningMalarkeyTeamUpgrade
import com.bluedragonmc.games.bedwars.upgrades.TeamUpgrade
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.combat.ProjectileModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.NPCModule
import com.bluedragonmc.server.module.gameplay.ShopModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.gameplay.WorldPermissionsModule
import com.bluedragonmc.server.module.instance.InstanceContainerModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.ChestModule
import com.bluedragonmc.server.module.vanilla.ItemDropModule
import com.bluedragonmc.server.module.vanilla.ItemPickupModule
import com.bluedragonmc.server.module.vanilla.NaturalRegenerationModule
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.surroundWithSeparators
import com.bluedragonmc.server.utils.toPlainText
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import java.nio.file.Paths
import java.util.*

class BedWarsGame(mapName: String) : Game("BedWars", mapName) {

    private val otherTeamBedDestroyedSound =
        Sound.sound(SoundEvent.ENTITY_ENDER_DRAGON_GROWL, Sound.Source.HOSTILE, 1.0f, 1.0f)
    private val bedDestroyedSound =
        Sound.sound(SoundEvent.ENTITY_WITHER_DEATH, Sound.Source.HOSTILE, 1.0f, 1.0f)

    init {

        val config = use(ConfigModule("bedwars.yml")).getConfig()

        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(InstanceContainerModule())
        use(VoidDeathModule(32.0))
        use(
            CountdownModule(
                2,
                true,
                OldCombatModule(allowDamage = true, allowKnockback = true),
                NaturalRegenerationModule(),
                SpectatorModule(spectateOnDeath = false),
                TimedRespawnModule(seconds = 5)
            )
        )
        use(ProjectileModule())
        use(WinModule(WinModule.WinCondition.LAST_TEAM_ALIVE) { player, winningTeam ->
            if (player in winningTeam.players) 500 else 50
        })
        use(TeamModule(true, TeamModule.AutoTeamMode.PLAYER_COUNT, 1, teamsAutoAssignedCallback = {
            val spawnpointProvider = getModule<SpawnpointModule>().spawnpointProvider
            players.forEach {
                it.respawnPoint = spawnpointProvider.getSpawnpoint(it)
                it.teleport(it.respawnPoint)
            }
        }))
        use(SpawnpointModule(SpawnpointModule.TeamDatabaseSpawnpointProvider()))
        use(MOTDModule(translatable("game.bedwars.motd")))
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(ItemGeneratorsModule())
        use(AwardsModule())
        use(SidebarModule(name))
        use(NPCModule())
        use(ShopModule())
        use(ItemPickupModule())
        use(ChestModule())
        use(CustomDeathMessageModule())
        use(
            WorldPermissionsModule(
                allowBlockBreak = true, allowBlockPlace = true, allowBlockInteract = true, allowBreakMap = false
            )
        )
        use(ItemDropModule(dropAllOnDeath = true))
        use(
            KitsModule(
                showMenu = true,
                giveKitsOnStart = true,
                selectableKits = config.node("kits").getList(KitsModule.Kit::class.java)!!
            )
        )

        val sidebar = getModule<SidebarModule>()
        val teamModule = getModule<TeamModule>()
        lateinit var sidebarTeamsSection: SidebarModule.ScoreboardBinding

        handleEvent<GameStartEvent> {
            sidebarTeamsSection = sidebar.bind {
                teamModule.teams.map { t ->
                    "team-status-${t.name.toPlainText()}" to (t.name + Component.text(
                        ": ", NamedTextColor.GRAY
                    ) + (if (bedWarsTeamInfo[t]?.bedIntact != false) Component.text(
                        "✔", NamedTextColor.GREEN
                    )
                    else Component.text(
                        t.players.count { !getModule<SpectatorModule>().isSpectating(it) }, RED
                    )))
                }
            }
            for (team in getModule<TeamModule>().teams) {
                bedWarsTeamInfo[team] = BedWarsTeamInfo(bedIntact = true)
            }
            sidebarTeamsSection.update()
        }

        dependingOn(TeamModule::class) {
            handleEvent<PlayerBlockBreakEvent> { event ->
                val team = bedBlockToTeam(event.block) ?: return@handleEvent
                if (team == getModule<TeamModule>().getTeam(event.player)) {
                    event.player.sendMessage(
                        translatable("game.bedwars.error.break_own_bed", RED)
                    )
                    event.isCancelled = true
                    return@handleEvent
                }
                if (!bedWarsTeamInfo.containsKey(team)) bedWarsTeamInfo[team] = BedWarsTeamInfo(false)
                else bedWarsTeamInfo[team]!!.bedIntact = false
                sidebarTeamsSection.update()
                for (player in players) {
                    player.sendMessage(
                        translatable(
                            "game.bedwars.bed_broken",
                            BRAND_COLOR_PRIMARY_2,
                            team.name,
                            event.player.name
                        ).surroundWithSeparators()
                    )
                    if (!team.players.contains(player)) {
                        player.playSound(otherTeamBedDestroyedSound)
                    } else {
                        player.showTitle(
                            Title.title(
                                translatable("game.bedwars.title.bed_broken", RED, TextDecoration.BOLD),
                                translatable("game.bedwars.subtitle.bed_broken", RED)
                            )
                        )
                        player.playSound(bedDestroyedSound)
                    }
                }

                // Break both parts of the bed
                var facing = BlockFace.valueOf(event.block.getProperty("facing").uppercase(Locale.getDefault()))
                if (event.block.getProperty("part") == "head") facing = facing.oppositeFace
                event.instance.setBlock(event.blockPosition.relative(facing), Block.AIR)
            }

            handleEvent<PlayerBlockPlaceEvent> { event ->
                val team = getModule<TeamModule>().getTeam(event.player) ?: return@handleEvent
                if (event.block.registry().material() == Material.WHITE_WOOL) event.block =
                    teamToWoolBlock[team.name.color()] ?: Block.WHITE_WOOL
            }

            handleEvent<PlayerDeathEvent> { event ->
                val team = getModule<TeamModule>().getTeam(event.player)
                if (bedWarsTeamInfo[team]?.bedIntact == false && !getModule<SpectatorModule>()
                        .isSpectating(event.player)
                ) {
                    getModule<SpectatorModule>().addSpectator(event.player)
                    sidebarTeamsSection.update()
                }
            }
        }

        handleEvent<TimedRespawnModule.TimedRespawnEvent>(ShopModule::class) { event ->
            event.player.inventory.clear()
            event.player.inventory.chestplate = ItemStack.of(Material.LEATHER_CHESTPLATE)
            event.player.inventory.leggings = ItemStack.of(Material.LEATHER_LEGGINGS)
            event.player.inventory.setItemStack(0, ItemStack.of(Material.WOODEN_SWORD))
        }

        handleEvent<WorldPermissionsModule.PreventPlayerBreakMapEvent> { event ->
            event.isCancelled = bedBlockToTeam.containsKey(event.block.registry().material())
        }

        onGameStart(ItemGeneratorsModule::class, NPCModule::class) {
            val generatorsModule = getModule<ItemGeneratorsModule>()
            val (spawnGenerators, diamondGenerators, emeraldGenerators, mainShopkeepers, teamUpgradeShopkeepers) = mapData!!.additionalLocations

            generatorsModule.addGenerator(config, "spawn", spawnGenerators)
            generatorsModule.addGenerator(config, "diamond", diamondGenerators)
            generatorsModule.addGenerator(config, "emerald", emeraldGenerators)

            getModule<NPCModule>().addNPC(instance = getInstance(),
                positions = mainShopkeepers,
                customName = translatable(
                    "game.bedwars.npc.shop",
                    NamedTextColor.YELLOW,
                    TextDecoration.BOLD
                ),
                skin = NPCModule.NPCSkins.WEIRD_FARMER.skin,
                entityType = EntityType.PLAYER,
                interaction = {
                    openShop(it.player)
                })

            getModule<NPCModule>().addNPC(instance = getInstance(),
                positions = teamUpgradeShopkeepers,
                customName = translatable(
                    "game.bedwars.npc.upgrades",
                    NamedTextColor.YELLOW,
                    TextDecoration.BOLD
                ),
                skin = NPCModule.NPCSkins.WEIRD_FARMER.skin,
                entityType = EntityType.PLAYER,
                interaction = {
                    openUpgradesMenu(it.player)
                })
        }

        use(StatisticsModule())

        ready()
    }

    private fun ItemGeneratorsModule.addGenerator(config: ConfigurationNode, type: String, locations: List<Pos>) {
        val map = config.node("generators", type).childrenList().map { node ->
            node.node("item").get<ItemStack>()!! to node.node("cooldown").getInt(Int.MAX_VALUE)
        }
        addGenerator(getInstance(), locations, mapOf(*map.toTypedArray()))
    }

    private val shop by lazy {
        getModule<ShopModule>().createShop(translatable("game.bedwars.menu.shop.title")) {
            val config = getModule<ConfigModule>().getConfig()
            for (item in config.node("shop").childrenList()) {
                val row = item.node("row").int
                val col = item.node("column").int
                val price = item.node("price").int
                val currency = item.node("currency").get<Material>()!!
                if (item.hasChild("item")) {
                    item(row, col, item.node("item").get<ItemStack>()!!, price, currency)
                } else {
                    val material = item.node("material").get<Material>()!!
                    val amount = item.node("amount").getInt(1)
                    item(row, col, material, amount, price, currency)
                }
            }
        }
    }

    private fun openShop(player: Player) {
        if (player.gameMode != GameMode.SPECTATOR) shop.open(player)
    }

    private val teamUpgrades = mutableMapOf<String, ShopModule.VirtualItem>()

    private fun registerUpgrades() {
        registerTeamUpgrade(FastFeetTeamUpgrade())
        registerTeamUpgrade(MiningMalarkeyTeamUpgrade())
    }

    private fun registerTeamUpgrade(upgrade: TeamUpgrade) {
        teamUpgrades[upgrade::class.qualifiedName!!] = upgrade.virtualItem
    }

    private val upgrades by lazy {
        registerUpgrades()
        getModule<ShopModule>().createShop(translatable("game.bedwars.menu.upgrades.title")) {
            val config = getModule<ConfigModule>().getConfig()
            for (upgrade in config.node("upgrades").childrenList()) {
                val row = upgrade.node("row").int
                val col = upgrade.node("column").int
                val price = upgrade.node("price").int
                val currency = upgrade.node("currency").get<Material>()!!
                teamUpgrade(row, col, price, currency, teamUpgrades[upgrade.node("upgrade").string]!!)
            }
        }
    }

    private fun openUpgradesMenu(player: Player) {
        if (player.gameMode != GameMode.SPECTATOR) upgrades.open(player)
    }

    private val bedBlockToTeam = mapOf(
        Material.RED_BED to RED,
        Material.BLUE_BED to BLUE,
        Material.GREEN_BED to GREEN,
        Material.CYAN_BED to AQUA,
        Material.PINK_BED to LIGHT_PURPLE,
        Material.WHITE_BED to WHITE,
        Material.GRAY_BED to GRAY,
        Material.YELLOW_BED to YELLOW,
        Material.ORANGE_BED to GOLD,
        Material.PURPLE_BED to DARK_PURPLE,
    )

    private val teamToWoolBlock = mapOf(
        RED to Block.RED_WOOL,
        BLUE to Block.BLUE_WOOL,
        GREEN to Block.GREEN_WOOL,
        AQUA to Block.CYAN_WOOL,
        LIGHT_PURPLE to Block.PINK_WOOL,
        WHITE to Block.WHITE_WOOL,
        GRAY to Block.GRAY_WOOL,
        YELLOW to Block.YELLOW_WOOL,
        GOLD to Block.ORANGE_WOOL,
        DARK_PURPLE to Block.PURPLE_WOOL
    )

    private fun bedBlockToTeam(bed: Block): TeamModule.Team? {
        return getModule<TeamModule>().getTeam(bedBlockToTeam[bed.registry().material()] ?: return null)
    }

    /*
    Team info includes:
    - List of team upgrades
    - Bed status
     */
    private val bedWarsTeamInfo = hashMapOf<TeamModule.Team, BedWarsTeamInfo>()

    class BedWarsTeamInfo(var bedIntact: Boolean = true)

}