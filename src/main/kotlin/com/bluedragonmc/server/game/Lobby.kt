package com.bluedragonmc.server.game

import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.*
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.queue.Queue
import com.bluedragonmc.server.utils.CircularList
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.surroundWithSeparators
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import java.nio.file.Paths
import java.time.Duration

class Lobby : Game("Lobby", "lobbyv2.1") {
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
            // 0.5, 62.5, -35.5, 0.0, 0.0 CENTER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(0.5, 62.5, -35.5, 0F, 0F),
                customName = Component.text("WackyMaze", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.EX4.skin,
                interaction = {
                    queue.queue(it.player, GameType("WackyMaze", null, "Islands"))
                })
            // -3.5, 62.5, -34.5, 0.0, 0.0 LEFT OF CENTER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(-3.5, 62.5, -34.5, 0F, 0F),
                customName = Component.text("BedWars", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.BED_HEAD.skin,
                interaction = {
                    queue.queue(it.player, GameType("BedWars", null, "Caves"))
                })
            // 4.5, 62.5, -34.5, 0.0, 0.0 RIGHT OF CENTER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(4.5, 62.5, -34.5, 0F, 0F),
                customName = Component.text("SkyWars", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.SKY.skin,
                interaction = {
                    queue.queue(it.player, GameType("SkyWars", null, null))
                })

            // -5.5, 62.5, -32.5, 0.0, 0.0 LEFT OF LEFT OF CENTER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(-5.5, 62.5, -32.5, 0F, 0F),
                customName = Component.text("FastFall", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.STUMBLE_GUY.skin,
                interaction = {
                    queue.queue(it.player, GameType("FastFall", null, null))
                })

            // 6.5, 62.5, -32.5, 0.0, 0.0 RIGHT OF RIGHT OF CENTER
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(6.5, 62.5, -32.5, 0F, 0F),
                customName = Component.text("Infection", NamedTextColor.YELLOW, TextDecoration.BOLD),
                entityType = EntityType.ZOMBIE,
                interaction = {
                    queue.queue(it.player, GameType("Infection", null, null))
                })

            // GAME SELECT (left)
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(-2.5, 61.0, -18.5, 0F, 0F),
                customName = Component.text("All Games", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.GAME_SELECT.skin,
                lookAtPlayer = false,
                interaction = {
//                    queue.queue(it.player, GameType("Infection", null, null))
                })

            // RANDOM GAME (right)
            addNPC(instance = this@Lobby.getInstance(),
                position = Pos(3.5, 61.0, -18.5, 0F, 0F),
                customName = Component.text("Random Game", NamedTextColor.YELLOW, TextDecoration.BOLD),
                skin = NPCModule.NPCSkins.RANDOM_GAME.skin,
                lookAtPlayer = false,
                interaction = {
                    queue.queue(it.player, GameType(Queue.gameClasses.keys.random(), null, null))
                })
        }

        use(object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
                    loadXPBar(event.player)
                }
            }

        })
        use(DoubleJumpModule)

        val tips = CircularList(listOf(
            Component.text("Click to join our community forum for announcements, giveaways, events, and discussions!", BRAND_COLOR_PRIMARY_2).clickEvent(
                ClickEvent.openUrl("https://bluedragonmc.com")),
            Component.text("Click to join our Discord server for announcements, giveaways, events, discussions, and sneak peeks!", BRAND_COLOR_PRIMARY_2).clickEvent(
                ClickEvent.openUrl("https://discord.gg/3gvSPdW")),
            ("Be sure to try out our newest game: " withColor BRAND_COLOR_PRIMARY_2) +
                    ("Infection" withColor BRAND_COLOR_PRIMARY_1) +
                    ("!" withColor BRAND_COLOR_PRIMARY_2),
            "Did you know: You can now double jump in the lobby!" withColor BRAND_COLOR_PRIMARY_2,
            "Did you know: Puffin is the name of our backend service." withColor BRAND_COLOR_PRIMARY_2,
            "Did you know: BlueDragon started as a Minecraft clans server." withColor BRAND_COLOR_PRIMARY_2,
        ).shuffled())
        var index = 0
        MinecraftServer.getSchedulerManager().buildTask {
            sendMessage(tips[index++].surroundWithSeparators())
            playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1.0F, 1.0F))
        }.repeat(Duration.ofMinutes(5
        )).schedule()

        ready()
    }

    fun loadXPBar(player: Player) {
        lateinit var loadXPTask: Task
        loadXPTask = MinecraftServer.getSchedulerManager().buildTask {
            val player = player as CustomPlayer
            if (!player.isDataInitialized()) return@buildTask
            val playerXP = player.data.experience
            val level = CustomPlayer.getXpLevel(playerXP)
            player.exp = CustomPlayer.getXpPercent(level)
            player.level = level.toInt()
            loadXPTask.cancel()
        }.repeat(Duration.ofMillis(20)).schedule()
    }
}