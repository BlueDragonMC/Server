package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.module.GameModule
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
                        Component.text("YOU DIED", NamedTextColor.RED),
                        Component.text("Respawning in $seconds seconds...", NamedTextColor.RED)
                    )
                )
                MinecraftServer.getSchedulerManager().buildTask {
                    parent.callEvent(TimedRespawnEvent(parent, event.player))
                    if (parent.hasModule<PlayerResetModule>()) {
                        val mode = parent.getModule<PlayerResetModule>().defaultGameMode
                        if (mode != null) event.player.gameMode = mode
                    }
                    event.player.teleport(event.player.respawnPoint)
                }.delay(Duration.ofSeconds(seconds.toLong())).schedule()
            }.delay(Duration.ofMillis(20)).schedule()
        }
    }

    class TimedRespawnEvent(game: Game, val player: Player) : GameEvent(game)
}