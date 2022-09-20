package com.bluedragonmc.games.lobby.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.SERVER_NAME_GRADIENT
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.CircularList
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.timer.Task
import net.minestom.server.utils.time.TimeUnit

class BossBarDisplayModule(components: List<Component>) : GameModule() {

    private val bossBarComponents = CircularList(components.map {
        SERVER_NAME_GRADIENT.decorate(TextDecoration.BOLD) + Component.text(" - ", NamedTextColor.GRAY) + it
    })

    private val bossBar =
        BossBar.bossBar(bossBarComponents.first(), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_20)
    private var index = 0
    private lateinit var task: Task

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.showBossBar(bossBar)
        }

        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            event.player.hideBossBar(bossBar)
        }

        task = MinecraftServer.getSchedulerManager().buildTask {
            val progress = this.bossBar.progress() - 0.01f
            if (progress < 0) {
                this.bossBar.progress(1.0f)
                this.bossBar.name(bossBarComponents[++index])
            } else {
                this.bossBar.progress(progress)
            }
        }.repeat(4, TimeUnit.SERVER_TICK).schedule()
    }

    override fun deinitialize() {
        if (::task.isInitialized) {
            task.cancel()
        }
        MinecraftServer.getBossBarManager().destroyBossBar(bossBar)
    }
}