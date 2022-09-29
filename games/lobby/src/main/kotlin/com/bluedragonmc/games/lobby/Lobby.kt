package com.bluedragonmc.games.lobby

import com.bluedragonmc.games.lobby.menu.*
import com.bluedragonmc.games.lobby.module.BossBarDisplayModule
import com.bluedragonmc.games.lobby.module.LeaderboardsModule
import com.bluedragonmc.games.lobby.module.ParkourModule
import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.*
import com.bluedragonmc.server.block.JukeboxMenuBlockHandler
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.CosmeticsModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.PlayerResetModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.minigame.VoidDeathModule
import com.bluedragonmc.server.utils.CircularList
import com.bluedragonmc.server.utils.noItalic
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventListener
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import java.nio.file.Paths
import java.time.Duration
import kotlin.collections.set

class Lobby : Game("Lobby", "lobbyv2.2") {

    override val autoRemoveInstance = false

    private val menus = mutableMapOf<String, LobbyMenu>()

    private fun registerMenu(menu: LobbyMenu, qualifier: String? = null) {
        val key = if (qualifier != null) menu::class.qualifiedName!! + "$" + qualifier
        else menu::class.qualifiedName!!
        menus[key] = menu
    }

    internal inline fun <reified T : LobbyMenu> getMenu(qualifier: String? = null): T? =
        getRegisteredMenu(
            if (qualifier != null) T::class.qualifiedName!! + "$" + qualifier else T::class.qualifiedName!!
        ) as? T

    internal fun getRegisteredMenu(key: String): LobbyMenu? = menus[key]

    init {

        val config = use(ConfigModule("lobby.yml")).getConfig()

        // World modules
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(SharedInstanceModule())

        // Player modules
        use(VoidDeathModule(32.0, respawnMode = true))
        use(InstantRespawnModule())
        use(SpawnpointModule(SpawnpointModule.SingleSpawnpointProvider(Pos(0.5, 64.0, 0.5, 180F, 0F))))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false))
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBlockInteract = true))

        // Combat zone
        use(OldCombatModule())
        // TODO make this configurable
        val combatZone = use(MapZonesModule()).createZone(Pos(14.0, 56.0, -44.0), Pos(-5.0, 64.0, -63.0), "Combat")
        use(CombatZonesModule(allowLeaveDuringCombat = false, minCombatSeconds = 10))
        use(CustomDeathMessageModule())
        combatZone.eventNode.addListener(MapZonesModule.PlayerPostEnterZoneEvent::class.java) { event ->
            val inv = event.player.inventory
            inv.helmet = ItemStack.of(Material.IRON_HELMET)
            inv.chestplate = ItemStack.of(Material.IRON_CHESTPLATE)
            inv.leggings = ItemStack.of(Material.IRON_LEGGINGS)
            inv.boots = ItemStack.of(Material.IRON_BOOTS)
            inv.setItemStack(0, ItemStack.of(Material.DIAMOND_SWORD))
        }
        combatZone.eventNode.addListener(MapZonesModule.PlayerPostLeaveZoneEvent::class.java) { event ->
            event.player.health = event.player.maxHealth
            event.player.inventory.clear()
            event.player.inventory.setItemStack(0, gameSelectItem)
        }

        // NPCs
        use(NPCModule()).apply {

            val npcs = config.node("npcs").getList(ConfigurableNPC::class.java)!!
            val gameNames = mutableSetOf<String>()

            npcs.forEach {
                it.game?.let { n -> gameNames.add(n) }
                addNPC(
                    instance = this@Lobby.getInstance(),
                    position = it.pos,
                    customName = it.name,
                    skin = it.skin,
                    lookAtPlayer = false,
                    interaction = { (player, _) ->
                        if (it.game != null) {
                            if (it.game == "random") {
                                Environment.current.queue.queue(player, GameType(gameNames.random(), null, null))
                            } else {
                                Environment.current.queue.queue(player, GameType(it.game, it.mode, it.map))
                            }
                        }
                        if (it.menu != null) {
                            val menu = getRegisteredMenu(it.menu)
                            menu?.open(player)
                        }
                    },
                    entityType = it.entityType ?: EntityType.PLAYER
                ).run {
                    if (it.lookAt != null) lookAt(it.lookAt)
                }
            }
        }

        val splashes = config.node("splashes").getList(String::class.java)!!
        val bossBars = config.node("boss-bars").getList(Component::class.java)!!

        use(BossBarDisplayModule(bossBars))
        use(StatisticsModule())
        use(CosmeticsModule())
        use(LobbyCosmeticsModule())
        use(object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
                    loadXPBar(event.player)
                    event.player.showTitle(Title.title(
                        SERVER_NAME_GRADIENT,
                        Component.translatable(splashes.random(), BRAND_COLOR_PRIMARY_2),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(100))
                    ))
                    event.player.inventory.setItemStack(0, gameSelectItem)
                }
                eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
                    if (event.itemStack.isSimilar(gameSelectItem)) {
                        getMenu<GameSelector>()!!.open(event.player)
                    }
                }
                eventNode.addListener(
                    EventListener.builder(InventoryPreClickEvent::class.java).ignoreCancelled(false).handler { event ->
                        if (event.clickedItem.isSimilar(gameSelectItem)) {
                            getMenu<GameSelector>()!!.open(event.player)
                        }
                    }.build()
                )
                eventNode.addListener(PlayerDeathEvent::class.java) { event ->
                    event.player.inventory.clear()
                    event.player.inventory.setItemStack(0, gameSelectItem)
                }
            }
        })
        use(DoubleJumpModule())
        use(GuiModule())
        use(LeaderboardsModule(config))
        use(ParkourModule(config.node("parkour")))

        val games = config.node("games").getList(GameEntry::class.java)!!

        registerMenu(LeaderboardBrowser(config, this))
        registerMenu(GameSelector(config, this))
        registerMenu(LobbyShop(config, this))
        registerMenu(RandomGameMenu(games, this))

        for (entry in games) {
            val gameName = entry.game
            val parent: Lobby = this
            // Register a menu for each game type
            registerMenu(GameMenu(entry, parent), gameName)
            registerMenu(MapSelectMenu(gameName, parent), gameName)
        }

        val tips = CircularList(config.node("tips").getList(Component::class.java)!!.shuffled())
        var index = 0
        MinecraftServer.getSchedulerManager().buildTask {
            getInstance().sendMessage(tips[index++].surroundWithSeparators())
            playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1.0F, 1.0F))
        }.repeat(Duration.ofMinutes(5)).schedule()

        getInstance().setBlock(0, 60, -19, Block.JUKEBOX.withHandler(JukeboxMenuBlockHandler(getInstance(), 0, 60, -19)))

        ready()
    }

    private val gameSelectItem =
        ItemStack.of(Material.COMPASS)
            .withDisplayName((Component.translatable("lobby.game_menu", ALT_COLOR_1)).noItalic())

    fun loadXPBar(player: Player) {
        lateinit var loadXPTask: Task
        loadXPTask = MinecraftServer.getSchedulerManager().buildTask {
            player as CustomPlayer
            if (!player.isDataInitialized()) return@buildTask
            val playerXP = player.data.experience
            val level = CustomPlayer.getXpLevel(playerXP)
            player.exp = CustomPlayer.getXpPercent(level)
            player.level = level.toInt()
            loadXPTask.cancel()
        }.repeat(Duration.ofMillis(20)).schedule()
    }

    abstract class LobbyMenu {

        private var isPopulated: Boolean = false
        protected abstract val menu: GuiModule.Menu
        abstract fun populate()

        fun open(player: Player) {
            if (!isPopulated) {
                populate()
                isPopulated = true
            }
            menu.open(player)
        }
    }
}