package com.bluedragonmc.server.game

import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.*
import com.bluedragonmc.server.block.JukeboxMenuBlockHandler
import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.database.StatisticsModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.PlayerResetModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.minigame.VoidDeathModule
import com.bluedragonmc.server.queue.Queue
import com.bluedragonmc.server.utils.*
import com.mongodb.internal.operation.OrderBy
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.entity.hologram.Hologram
import net.minestom.server.entity.metadata.other.ItemFrameMeta.Orientation
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.nio.file.Paths
import java.time.Duration
import javax.imageio.ImageIO

class Lobby : Game("Lobby", "lobbyv2.1") {

    override val autoRemoveInstance = false

    private val queue = Environment.current.queue

    @ConfigSerializable
    data class ConfigurableNPC(
        val pos: Pos = Pos.ZERO,
        val name: Component = Component.empty(),
        val entityType: EntityType? = null,
        val skin: PlayerSkin? = null,
        val game: String? = null,
        val menu: String? = null,
        val map: String? = null,
        val mode: String? = null,
        val lookAt: Pos? = null
    )

    @ConfigSerializable
    data class GameEntry(
        val game: String = "???",
        val category: String = "???",
        val description: String = "???",
        val time: String = "\u221e",
        val material: Material = Material.RED_STAINED_GLASS
    )

    @ConfigSerializable
    data class Leaderboard(
        val statistic: String = "",
        val title: String = "Failed to load leaderboard",
        val subtitle: String = "",
        val show: Int = 10,
        val displayMode: DisplayMode = DisplayMode.WHOLE_NUMBER,
        val orderBy: OrderBy = OrderBy.DESC,
        val topLeft: Pos = Pos.ZERO,
        val bottomRight: Pos = Pos.ZERO,
        val orientation: Orientation = Orientation.EAST
    ) {
        enum class DisplayMode {
            DURATION, DECIMAL, WHOLE_NUMBER
        }
    }

    @ConfigSerializable
    data class LeaderboardCategory(
        val name: String = "",
        val description: String = "",
        val icon: Material = Material.WHITE_STAINED_GLASS,
        val leaderboards: List<LeaderboardEntry> = emptyList()
    )

    @ConfigSerializable
    data class LeaderboardEntry(
        val title: String = "",
        val subtitle: String = "",
        val icon: Material = Material.WHITE_STAINED_GLASS,
        val statistic: String = "",
        val displayMode: Leaderboard.DisplayMode = Leaderboard.DisplayMode.WHOLE_NUMBER,
        val orderBy: OrderBy = OrderBy.DESC
    )

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

        val menus = mutableMapOf<String, GuiModule.Menu>()

        // NPCs
        use(NPCModule())
        getModule<NPCModule>().apply {

            val npcs = config.node("npcs").getList(ConfigurableNPC::class.java)!!

            npcs.forEach {
                addNPC(
                    instance = this@Lobby.getInstance(),
                    position = it.pos,
                    customName = it.name,
                    skin = it.skin,
                    lookAtPlayer = false,
                    interaction = { (player, _) ->
                        if (it.game != null)
                            queue.queue(player, GameType(it.game, it.mode, it.map))
                        if(it.menu != null)
                            menus[it.menu]?.open(player)
                    },
                    entityType = it.entityType ?: EntityType.PLAYER
                ).run {
                    if(it.lookAt != null) lookAt(it.lookAt)
                }
            }

            // GAME SELECT (left)
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(-2.5, 61.0, -18.5),
                customName = Component.translatable("lobby.npc.all_games", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.GAME_SELECT.skin,
                lookAtPlayer = false,
                interaction = {
                    gameSelect.open(it.player)
                })

            // RANDOM GAME (right)
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(3.5, 61.0, -18.5),
                customName = Component.translatable("lobby.npc.random_game", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.RANDOM_GAME.skin,
                lookAtPlayer = false,
                interaction = {
                    queue.queue(it.player, GameType(Queue.gameClasses.keys.random(), null, null))
                })

            // SHOPKEEPER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(-12.5, 61.0, -6.5, -145.0f, 0.0f),
                customName = Component.translatable("lobby.npc.shop", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.SHOP.skin,
                lookAtPlayer = false,
                enableFullSkin = false,
                interaction = {
                    it.player.sendMessage(Component.translatable("lobby.shop.coming_soon", ALT_COLOR_1))
                })
        }

        val splashes = config.node("splashes").getList(String::class.java)!!

        use(StatisticsModule())
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
                eventNode.addListener(DataLoadedEvent::class.java) { event ->
                    getModule<StatisticsModule>().recordStatistic(event.player, "times_data_loaded") { i -> i?.plus(1.0) ?: 1.0 }
                }
                eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
                    if (event.itemStack.material() == Material.COMPASS) gameSelect.open(event.player)
                }
            }
        })
        use(DoubleJumpModule())
        use(GuiModule())

        populateGameSelector(config)
        populateLeaderboardBrowser(config)

        MinecraftServer.getSchedulerManager().buildTask {
            // Re-create the leaderboard browser GUIs every 5 minutes to use new information
            populateLeaderboardBrowser(config)
        }.repeat(Duration.ofMinutes(5)).delay(Duration.ofMinutes(5)).schedule()
        menus["leaderboard_browser"] = leaderboardBrowser

        val tips = CircularList(config.node("tips").getList(Component::class.java)!!.shuffled())
        var index = 0
        MinecraftServer.getSchedulerManager().buildTask {
            getInstance().sendMessage(tips[index++].surroundWithSeparators())
            playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1.0F, 1.0F))
        }.repeat(Duration.ofMinutes(5)).schedule()

        Hologram(getInstance(),
            Pos(16.975, 63.65, 0.0),
            Component.translatable("lobby.hologram.parkour", NamedTextColor.GREEN, TextDecoration.BOLD),
            true)

        // Font is from https://www.1001freefonts.com/minecraft.font
        val baseFont = Font.createFont(Font.TRUETYPE_FONT, this::class.java.getResourceAsStream("/font/Minecraft.otf"))
        val font18 = baseFont.deriveFont(Font.PLAIN, 18f)
        val font36 = baseFont.deriveFont(Font.PLAIN, 36f)
        val font72 = baseFont.deriveFont(Font.PLAIN, 72f)
        val leaderboards = config.node("leaderboards").getList(Leaderboard::class.java)

        // Create a list of font sizes to use for dynamic text scaling
        val fontSizes = (12 .. 36 step 2).map { baseFont.deriveFont(Font.PLAIN, it.toFloat()) }

        MapUtils.createMaps(getInstance(), Pos(-19.0, 64.0, -17.0), Pos(-19.0, 62.0, -23.0), Orientation.EAST) { graphics ->
            val imageStream = Lobby::class.java.getResourceAsStream("/bd-banner.png")!!
            val image = ImageIO.read(imageStream)
            val scale = (128 * 7) / image.width.toDouble()
            graphics.background = Color.WHITE
            graphics.clearRect(0, 0, 128 * 7, 128 * 3)
            graphics.drawRenderedImage(image, AffineTransform.getScaleInstance(scale, scale))
            graphics.font = font36
            graphics.color = Color.BLACK
            LOBBY_NEWS_ITEMS.forEachIndexed { index, str ->
                graphics.drawString(str, 10, 200 + index * 30)
            }
            graphics.color = Color(BRAND_COLOR_PRIMARY_3.value())
            graphics.font = font18
            graphics.drawString("Join our community at bluedragonmc.com", 10f, 128f * 3f - 10f)
        }

        leaderboards?.forEachIndexed { lbIndex, lb ->
            MinecraftServer.getSchedulerManager().buildTask {
                MapUtils.createMaps(getInstance(), lb.topLeft, lb.bottomRight, lb.orientation, 5000 + lbIndex * 200) { graphics ->
                    graphics.font = font72
                    graphics.drawString(lb.title, 10f, 70f)
                    graphics.color = Color(0x727272)
                    graphics.font = font36
                    graphics.drawString(lb.subtitle, 10f, 110f)
                    runBlocking {
                        val leaderboardPlayers = getModule<StatisticsModule>().rankPlayersByStatistic(lb.statistic, lb.orderBy, 10)
                        val it = leaderboardPlayers.entries.iterator()
                        graphics.color = Color.WHITE
                        for (i in 1 .. lb.show) {
                            // Draw leaderboard numbers (default: 1-10)
                            val lineStartY = 130f + 30f * i
                            graphics.drawString("$i.", 10f, lineStartY)

                            if (!it.hasNext()) continue
                            val (player, value) = it.next()

                            // Create the text to display based on the display mode
                            val displayText = formatValue(value, lb.displayMode)
                            // Draw player name
                            val remainingPixels = 128 * 4 - stringWidth(graphics, font36, displayText) - 60f - 10f
                            val playerNameFont = fontSizes.lastOrNull {
                                stringWidth(graphics, it, player.username) < remainingPixels
                            } ?: continue
                            graphics.drawString(player.username, 60f, lineStartY, Color(player.highestGroup?.color?.value() ?: 0x727272), playerNameFont)
                            // Draw leaderboard value
                            graphics.drawString(displayText, 128 * 4 - stringWidth(graphics, displayText) - 10f, lineStartY, Color.WHITE, font36)
                        }
                    }
                }
            }.repeat(Duration.ofMinutes(5)).schedule()
        }

        getInstance().setBlock(0, 60, -19, Block.JUKEBOX.withHandler(JukeboxMenuBlockHandler(getInstance(), 0, 60, -19)))

        ready()
    }

    private fun formatValue(value: Double, displayMode: Leaderboard.DisplayMode) = when(displayMode) {
        Leaderboard.DisplayMode.DURATION -> {
            val duration = Duration.ofMillis(value.toLong())
            String.format("%02d:%02d:%02d.%03d", duration.toHoursPart(), duration.toMinutesPart(), duration.toSecondsPart(), duration.toMillisPart())
        }
        Leaderboard.DisplayMode.DECIMAL -> String.format("%.2f", value)
        Leaderboard.DisplayMode.WHOLE_NUMBER -> value.toInt().toString()
    }

    private fun Graphics2D.drawString(string: String, x: Float, y: Float, color: Color, font: Font) {
        this.font = font
        this.color = color
        drawString(string, x, y)
    }

    private fun stringWidth(graphics: Graphics2D, string: String) =
        graphics.font.getStringBounds(string, FontRenderContext(graphics.transform, false, false)).width.toFloat()

    private fun stringWidth(graphics: Graphics2D, font: Font, string: String) =
        font.getStringBounds(string, FontRenderContext(graphics.transform, false, false)).width.toFloat()

    private val gameSelectItem =
        ItemStack.of(Material.COMPASS).withDisplayName((Component.translatable("lobby.game_menu", ALT_COLOR_1)).noItalic())

    private lateinit var gameSelect: GuiModule.Menu
    private lateinit var leaderboardBrowser: GuiModule.Menu

    private fun populateLeaderboardBrowser(config: ConfigurationNode) {
        val categories = config.node("leaderboard-browser").getList(LeaderboardCategory::class.java)!!
        leaderboardBrowser = getModule<GuiModule>().createMenu(Component.translatable("lobby.menu.lb.title"), InventoryType.CHEST_3_ROW, false, true) {
            categories.forEachIndexed { categoryIndex, category ->
                // Build a menu for each leaderboard in the category
                val lbMenu = getModule<GuiModule>().createMenu(Component.translatable(category.name), InventoryType.CHEST_3_ROW, false, true) {
                    slot(26, Material.ARROW, {
                        displayName(Component.translatable("lobby.menu.lb.back", NamedTextColor.RED, Component.translatable(category.name)).noItalic())
                    }) { leaderboardBrowser.open(player) }
                    for ((entryIndex, entry) in category.leaderboards.withIndex()) {
                        slot(entryIndex, entry.icon, {
                            displayName(Component.translatable(entry.title, BRAND_COLOR_PRIMARY_2).noItalic())

                            val leaderboardComponent = runBlocking {
                                getModule<StatisticsModule>().rankPlayersByStatistic(entry.statistic, entry.orderBy)
                            }.map { (doc, value) ->
                                Component.text(doc.username, doc.highestGroup?.color ?: NamedTextColor.GRAY).noItalic() +
                                        Component.text(": ", BRAND_COLOR_PRIMARY_2) +
                                        Component.text(formatValue(value, entry.displayMode), BRAND_COLOR_PRIMARY_1)
                            }

                            if(entry.subtitle.isNotEmpty()) {
                                lore(Component.translatable(entry.subtitle, BRAND_COLOR_PRIMARY_1).noItalic(),
                                    Component.empty(),
                                    *leaderboardComponent.toTypedArray())
                            } else {
                                lore(*leaderboardComponent.toTypedArray())
                            }
                        })
                    }
                }
                slot(categoryIndex, category.icon, { displayName(Component.translatable(category.name, BRAND_COLOR_PRIMARY_1).noItalic()) }) { lbMenu.open(player) }
            }
            slot(26, Material.ARROW, { displayName(Component.translatable("lobby.menu.lb.exit", NamedTextColor.RED).noItalic()) }) { menu.close(player) }
        }
    }

    private fun populateGameSelector(config: ConfigurationNode) {
        val games = config.node("games").getList(GameEntry::class.java)!!
        gameSelect = getModule<GuiModule>().createMenu(Component.translatable("lobby.menu.game.title"), InventoryType.CHEST_1_ROW,
            isPerPlayer = true,
            allowSpectatorClicks = true
        ) {
            games.forEachIndexed { index, (game, category, desc, time, material) ->
                slot(index, material, { player ->
                    displayName(Component.text(game, Style.style(TextDecoration.BOLD)).noItalic()
                        .withGradient(BRAND_COLOR_PRIMARY_1, BRAND_COLOR_PRIMARY_3))
                    val lore = splitAndFormatLore(miniMessage.deserialize(desc), ALT_COLOR_1, player).toMutableList()
                    lore.add(0, Component.text("$category \u2014 \u231a $time", NamedTextColor.RED).noItalic())
                    lore(lore)
                }) {
                    queue.queue(player, GameType(game))
                    menu.close(player)
                }
            }
        }
    }

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
}