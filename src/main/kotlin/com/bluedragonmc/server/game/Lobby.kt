package com.bluedragonmc.server.game

import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.*
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.queue.Queue
import com.bluedragonmc.server.utils.*
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.hologram.Hologram
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import java.nio.file.Paths
import java.time.Duration
import java.util.*

class Lobby : Game("Lobby", "lobbyv2.1") {

    override val autoRemoveInstance = false

    private val splashes = listOf(
        "Welcome back!",
        "Try Infinijump!",
        "Enjoy your stay!",
        "Professional software!",
        "Have you seen Joe?"
    )

    init {
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

            val center = Pos(0.5, 64.25, -31.5)

            // 0.5, 62.5, -35.5, 0.0, 0.0 CENTER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(0.5, 62.5, -35.5),
                customName = Component.text("WackyMaze", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.EX4.skin,
                lookAtPlayer = false,
                interaction = {
                    queue.queue(it.player, GameType("WackyMaze", null, "Islands"))
                }).lookAt(center)
            // -3.5, 62.5, -34.5, 0.0, 0.0 LEFT OF CENTER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(-3.5, 62.5, -34.5),
                customName = Component.text("BedWars", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.BED_HEAD.skin,
                lookAtPlayer = false,
                interaction = {
                    queue.queue(it.player, GameType("BedWars", null, "Caves"))
                }).lookAt(center)
            // 4.5, 62.5, -34.5, 0.0, 0.0 RIGHT OF CENTER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(4.5, 62.5, -34.5),
                customName = Component.text("SkyWars", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.SKY.skin,
                lookAtPlayer = false,
                interaction = {
                    queue.queue(it.player, GameType("SkyWars", null, null))
                }).lookAt(center)

            // -5.5, 62.5, -32.5, 0.0, 0.0 LEFT OF LEFT OF CENTER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(-5.5, 62.5, -32.5),
                customName = Component.text("FastFall", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.STUMBLE_GUY.skin,
                lookAtPlayer = false,
                interaction = {
                    queue.queue(it.player, GameType("FastFall", null, null))
                }).lookAt(center)

            // 6.5, 62.5, -32.5, 0.0, 0.0 RIGHT OF RIGHT OF CENTER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(6.5, 62.5, -32.5),
                customName = Component.text("Infection", NamedTextColor.YELLOW, TextDecoration.BOLD),
                entityType = EntityType.ZOMBIE,
                lookAtPlayer = false,
                interaction = {
                    queue.queue(it.player, GameType("Infection", null, null))
                }).lookAt(center)

            // -7.5, 62.5, -30.5, 0.0, 0.0 FAR LEFT
            addNPC(instance = this@Lobby.getInstance(),
            position = Pos(-7.5, 62.5, -30.5),
            customName = Component.text("Infinijump", NamedTextColor.YELLOW, TextDecoration.BOLD),
            skin = NPCModule.NPCSkins.COOL_THING.skin,
            lookAtPlayer = false,
            interaction = {
                queue.queue(it.player, GameType("Infinijump", null, null))
            }).lookAt(center)

            // GAME SELECT (left)
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

            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(-12.5, 61.0, -6.5, -145.0f, 0.0f),
                customName = Component.text("Shop", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.SHOP.skin,
                lookAtPlayer = false,
                enableFullSkin = false,
                interaction = {
                    it.player.sendMessage("Coming soon..." withColor ALT_COLOR_1)
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
        use(DoubleJumpModule)
        use(GuiModule())

        val tips = CircularList(listOf(
            Component.text("Click to join our community forum for announcements, giveaways, events, and discussions!", BRAND_COLOR_PRIMARY_2).clickEvent(
                ClickEvent.openUrl("https://bluedragonmc.com")),
            Component.text("Click to join our Discord server for announcements, giveaways, events, discussions, and sneak peeks!", BRAND_COLOR_PRIMARY_2).clickEvent(
                ClickEvent.openUrl("https://discord.gg/3gvSPdW")),
            ("Be sure to try out our newest game: " withColor BRAND_COLOR_PRIMARY_2) +
                    ("Infinijump" withColor BRAND_COLOR_PRIMARY_1) +
                    ("!" withColor BRAND_COLOR_PRIMARY_2),
            "Did you know: You can now double jump in the lobby!" withColor BRAND_COLOR_PRIMARY_2,
            "Did you know: BlueDragon started as a Minecraft clans server." withColor BRAND_COLOR_PRIMARY_2,
            "Tip: don't go in the blue bus on the Airport map in PvPMaster." withColor BRAND_COLOR_PRIMARY_2,
        ).shuffled())
        var index = 0
        MinecraftServer.getSchedulerManager().buildTask {
            sendMessage(tips[index++].surroundWithSeparators())
            playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1.0F, 1.0F))
        }.repeat(Duration.ofMinutes(5
        )).schedule()

        Hologram(getInstance(), Pos(16.975, 63.65, 0.0), Component.text("World Tour Parkour", NamedTextColor.GREEN, TextDecoration.BOLD), true)

        ready()
    }

    private val gameSelectItem = ItemStack.of(Material.COMPASS).withDisplayName(("Game Menu" withColor ALT_COLOR_1).noItalic())
    private val gameSelect by lazy {
        getModule<GuiModule>().createMenu(Component.text("Game Select"), InventoryType.CHEST_1_ROW,
            isPerPlayer = false,
            allowSpectatorClicks = true
        ) {
            val propertiesFile = javaClass.classLoader.getResourceAsStream("game_info.properties")
            val properties = Properties()
            properties.load(propertiesFile)
            Queue.gameClasses.keys.forEachIndexed { index, key ->
                val material = Material.fromNamespaceId(properties.getProperty("game_${key}_material") ?: "minecraft:paper") ?: Material.PAPER
                val desc = properties.getProperty("game_${key}_description") ?: ""
                val category = properties.getProperty("game_${key}_category") ?: "Misc"
                slot(index, material, {
                    displayName(Component.text(key, Style.style(TextDecoration.BOLD)).noItalic().withGradient(BRAND_COLOR_PRIMARY_1, BRAND_COLOR_PRIMARY_3))
                    val lore = desc.split("\n").map { Component.text(it, NamedTextColor.YELLOW).noItalic() }.toMutableList()
                    lore.add(0, Component.text(category, NamedTextColor.RED).noItalic())
                    lore(lore)
                }) {
                    queue.queue(player, GameType(key))
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