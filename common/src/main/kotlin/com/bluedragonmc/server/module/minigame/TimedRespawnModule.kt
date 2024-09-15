package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.manage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDeathEvent
import java.time.Duration

class TimedRespawnModule(private val seconds: Int = 5) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            MinecraftServer.getSchedulerManager().buildTask {
                if (parent.getModule<SpectatorModule>().isSpectating(event.player)) return@buildTask
                event.player.respawn()
                event.player.gameMode = GameMode.SPECTATOR
                event.player.showTitle(
                    Title.title(
                        Component.translatable("module.respawn.title", NamedTextColor.RED),
                        Component.translatable("module.respawn.subtitle", NamedTextColor.RED, Component.text(seconds))
                    )
                )
                MinecraftServer.getSchedulerManager().buildTask {
                    parent.callEvent(TimedRespawnEvent(parent, event.player))
                    if (parent.hasModule<PlayerResetModule>()) {
                        val mode = parent.getModule<PlayerResetModule>().defaultGameMode
                        if (mode != null) event.player.gameMode = mode
                    }
                    event.player.teleport(event.player.respawnPoint)
                }.delay(Duration.ofSeconds(seconds.toLong())).schedule().manage(parent)
            }.delay(Duration.ofMillis(20)).schedule().manage(parent)
        }
    }

    class TimedRespawnEvent(game: Game, val player: Player) : GameEvent(game)
}