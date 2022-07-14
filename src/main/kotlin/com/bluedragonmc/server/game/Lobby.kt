package com.bluedragonmc.server.game

import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.gameplay.*
import com.bluedragonmc.server.module.instance.SharedInstanceModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.queue
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
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
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false, forcedItemSlot = null))
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
                skin = NPCModule.NPCSkins.SKY.skin,
                interaction = {
                    queue.queue(it.player, GameType("BedWars", null, "Caves"))
                })
        }

        use(object : GameModule() {
            override fun initialize(parent: Game, eventNode: EventNode<Event>) {
                eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
                    loadXPBar(event.player)
                }
            }

        })
        ready()
    }

    fun loadXPBar(player: Player) {
        lateinit var loadXPTask: Task
        loadXPTask = MinecraftServer.getSchedulerManager().buildTask {
            val player = player as CustomPlayer
            if (!player.isDataInitialized()) return@buildTask
            val playerXP = player.data.experience
            player.exp = CustomPlayer.getXpPercent(playerXP)
            player.level = CustomPlayer.getXpLevel(playerXP)
            loadXPTask.cancel()
        }.repeat(Duration.ofMillis(20)).schedule()
    }
}