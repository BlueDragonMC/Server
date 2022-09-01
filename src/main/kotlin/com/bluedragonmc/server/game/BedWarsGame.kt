package com.bluedragonmc.server.game

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.*
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
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.attribute.Attribute
import net.minestom.server.attribute.AttributeModifier
import net.minestom.server.attribute.AttributeOperation
import net.minestom.server.coordinate.Pos
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
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.sound.SoundEvent
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import java.nio.file.Paths
import java.util.*


class BedWarsGame(mapName: String) : Game("BedWars", mapName) {

    val otherTeamBedDestroyedSound = Sound.sound(SoundEvent.ENTITY_ENDER_DRAGON_GROWL, Sound.Source.HOSTILE, 1.0f, 1.0f)
    val bedDestroyedSound = Sound.sound(SoundEvent.ENTITY_WITHER_DEATH, Sound.Source.HOSTILE, 1.0f, 1.0f)

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
                SpectatorModule(spectateOnDeath = false),
                TimedRespawnModule(seconds = 5)
            )
        )
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
        use(MOTDModule(Component.translatable("game.bedwars.motd")))
        use(PlayerResetModule(defaultGameMode = GameMode.SURVIVAL))
        use(ItemGeneratorsModule())
        use(AwardsModule())
        use(SidebarModule(name))
        use(NPCModule())
        use(ShopModule())
        use(ItemPickupModule())
        use(ChestModule())
        use(NaturalRegenerationModule())
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
        use(object : GameModule() {

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
                                Component.translatable("game.bedwars.error.break_own_bed", NamedTextColor.RED)
                            )
                            event.isCancelled = true
                            return@addListener
                        }
                        if (!bedWarsTeamInfo.containsKey(team)) bedWarsTeamInfo[team] = BedWarsTeamInfo(false)
                        else bedWarsTeamInfo[team]!!.bedIntact = false
                        sidebarTeamsSection.update()
                        for (player in parent.players) {
                            player.sendMessage(
                                Component.translatable("game.bedwars.bed_broken", team.name, event.player.name)
                                    .surroundWithSeparators()
                            )
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
                    val (spawnGenerators, diamondGenerators, emeraldGenerators, mainShopkeepers, teamUpgradeShopkeepers) = parent.mapData!!.additionalLocations

                    generatorsModule.addGenerator(config, "spawn", spawnGenerators)
                    generatorsModule.addGenerator(config, "diamond", diamondGenerators)
                    generatorsModule.addGenerator(config, "emerald", emeraldGenerators)

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
            }
        })

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
        getModule<ShopModule>().createShop("Shop") {
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
    private val speedModifier = AttributeModifier("bluedragon:fastfeet", 0.4f, AttributeOperation.MULTIPLY_BASE)
    private val fastFeet = ShopModule.TeamUpgrade(
        "Fast Feet", "Gives Speed to all members on your team.", Material.IRON_BOOTS
    ) { player, _ ->
        player.getAttribute(Attribute.MOVEMENT_SPEED)
            .addModifier(speedModifier)
    }

    private val miningMalarkey = ShopModule.TeamUpgrade(
        "Mining Malarkey", "Gives Haste I to all members on your team.", Material.IRON_PICKAXE
    ) { player, _ -> player.addEffect(Potion(PotionEffect.HASTE, 1, Integer.MAX_VALUE, Potion.ICON_FLAG)) }

    private val upgrades by lazy {
        getModule<ShopModule>().createShop("Team Upgrades") {
            teamUpgrade(1, 1, 7, Material.DIAMOND, fastFeet)
            teamUpgrade(1, 2, 5, Material.DIAMOND, miningMalarkey)
        }
    }

    fun openShop(player: Player) {
        if (player.gameMode != GameMode.SPECTATOR) shop.open(player)
    }

    fun openUpgradesMenu(player: Player) {
        if (player.gameMode != GameMode.SPECTATOR) upgrades.open(player)
    }

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