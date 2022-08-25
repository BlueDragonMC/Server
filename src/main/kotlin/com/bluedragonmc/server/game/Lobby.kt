package com.bluedragonmc.server.game

import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.*
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.PlayerResetModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.minigame.VoidDeathModule
import com.bluedragonmc.server.queue.Queue
import com.bluedragonmc.server.utils.*
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
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.nio.file.Paths
import java.time.Duration

class Lobby : Game("Lobby", "lobbyv2.1") {

    override val autoRemoveInstance = false

    private val splashes = listOf(
        "Welcome back!",
        "Try Infinijump!",
        "Enjoy your stay!",
        "Professional software!",
        "Have you seen Joe?"
    )

    private val queue = Environment.current.queue

    @ConfigSerializable
    data class ConfigurableNPC(
        val pos: Pos = Pos.ZERO,
        val name: Component = Component.empty(),
        val entityType: EntityType? = null,
        val skin: PlayerSkin? = null,
        val game: String? = null,
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
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBlockInteract = false))

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
                    },
                    entityType = it.entityType ?: EntityType.PLAYER
                ).run {
                    if(it.lookAt != null) lookAt(it.lookAt)
                }
            }

            // GAME SELECT (left)
            // todo: waiting on https://github.com/Minestom/Minestom/pull/1275 to translate NPC names
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(-2.5, 61.0, -18.5),
                customName = Component.text("All Games", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.GAME_SELECT.skin,
                lookAtPlayer = false,
                interaction = {
                    gameSelect.open(it.player)
                })

            // RANDOM GAME (right)
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(3.5, 61.0, -18.5),
                customName = Component.text("Random Game", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.RANDOM_GAME.skin,
                lookAtPlayer = false,
                interaction = {
                    queue.queue(it.player, GameType(Queue.gameClasses.keys.random(), null, null))
                })

            // SHOPKEEPER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(-12.5, 61.0, -6.5, -145.0f, 0.0f),
                customName = Component.text("Shop", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.SHOP.skin,
                lookAtPlayer = false,
                enableFullSkin = false,
                interaction = {
                    it.player.sendMessage(Component.translatable("lobby.shop.coming_soon", ALT_COLOR_1))
                })
        }

        use(object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
                    loadXPBar(event.player)
                    event.player.showTitle(Title.title(
                        SERVER_NAME_GRADIENT,
                        splashes.random() withColor BRAND_COLOR_PRIMARY_2,
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(100))
                    ))
                    event.player.inventory.setItemStack(0, gameSelectItem)
                }
                eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
                    if (event.itemStack.material() == Material.COMPASS) gameSelect.open(event.player)
                }
            }
        })
        use(DoubleJumpModule())
        use(GuiModule())

        populateGameSelector(config)

        val tips = CircularList(config.node("tips").getList(Component::class.java)!!.shuffled())
        var index = 0
        MinecraftServer.getSchedulerManager().buildTask {
            getInstance().players.forEach {
                it.sendMessage(tips[index].surroundWithSeparators())
            }
            ++index
            playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1.0F, 1.0F))
        }.repeat(Duration.ofMinutes(5)).schedule()

        Hologram(getInstance(),
            Pos(16.975, 63.65, 0.0),
            Component.text("World Tour Parkour", NamedTextColor.GREEN, TextDecoration.BOLD),
            true)

        ready()
    }

    private val gameSelectItem =
        ItemStack.of(Material.COMPASS).withDisplayName(("Game Menu" withColor ALT_COLOR_1).noItalic())

    private lateinit var gameSelect: GuiModule.Menu

    private fun populateGameSelector(config: ConfigurationNode) {
        val games = config.node("games").getList(GameEntry::class.java)!!
        gameSelect = getModule<GuiModule>().createMenu(Component.text("Game Select"), InventoryType.CHEST_1_ROW,
            isPerPlayer = false,
            allowSpectatorClicks = true
        ) {
            games.forEachIndexed { index, (game, category, desc, time, material) ->
                slot(index, material, {
                    displayName(Component.text(game, Style.style(TextDecoration.BOLD)).noItalic()
                        .withGradient(BRAND_COLOR_PRIMARY_1, BRAND_COLOR_PRIMARY_3))
                    val lore =
                        desc.split("\\n").map { Component.text(it, NamedTextColor.YELLOW).noItalic() }.toMutableList()
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