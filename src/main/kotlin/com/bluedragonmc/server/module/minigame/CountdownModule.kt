package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.GameState
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket
import java.time.Duration
import java.util.*
import kotlin.concurrent.fixedRateTimer

class CountdownModule(
    private val threshold: Int,
    val allowMoveDuringCountdown: Boolean = true,
    vararg val useOnStart: GameModule
) : GameModule() {

    private var countdown: Timer? = null
    private var secondsLeft: Int? = null
    private var countdownRunning: Boolean = false
    private var countdownEnded: Boolean = false

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (countdownEnded) return@addListener
            if (threshold > 0 && parent.players.size >= threshold && countdown == null) {
                countdown = createCountdownTask(parent, 10)
                parent.state = GameState.STARTING
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (!countdownEnded) event.isCancelled = true
        }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (!countdownEnded) event.isCancelled = true
        }

        eventNode.addListener(RemoveEntityFromInstanceEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener
            if (threshold > 0 && countdown != null && parent.players.size < threshold) {
                // Stop the countdown
                cancelCountdown()
                parent.showTitle(
                    Title.title(
                        Component.text("Cancelled!", NamedTextColor.RED), Component.text(
                            "Not enough players to start (${parent.players.size}/$threshold)", NamedTextColor.RED
                        ), Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(1))
                    )
                )
                parent.state = GameState.WAITING
            }
        }
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (countdownRunning && // Countdown started
                !countdownEnded && // Countdown not ended
                !allowMoveDuringCountdown && (event.newPosition.x != event.player.position.x || event.newPosition.z != event.player.position.z)
            ) {
                // Revert the player's position without forcing the player's facing direction
                event.newPosition = event.player.position
                event.player.sendPacket(
                    PlayerPositionAndLookPacket(
                        event.player.position.withView(0.0f, 0.0f),
                        (0x08 or 0x10).toByte(), // flags - see https://wiki.vg/Protocol#Synchronize_Player_Position
                        event.player.nextTeleportId,
                        false
                    )
                )
            }
        }

        eventNode.addListener(GameStartEvent::class.java) { event ->
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                for (module in useOnStart) parent.use(module)
            }
            parent.state = GameState.INGAME
            countdownEnded = true
            parent.players.forEach { player ->
                player.askSynchronization()
            }
        }
    }

    private fun createCountdownTask(parent: Game, initialSeconds: Int): Timer {
        if (!allowMoveDuringCountdown) parent.players.forEach { it.teleport(it.respawnPoint) }
        secondsLeft = initialSeconds
        countdownRunning = true
        return fixedRateTimer("countdown", initialDelay = 1000, period = 1000) {
            val seconds = secondsLeft ?: run {
                cancelCountdown()
                return@fixedRateTimer
            }
            if (seconds > 0) {
                parent.showTitle(
                    Title.title(
                        Component.text(seconds, BRAND_COLOR_PRIMARY_2),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO)
                    )
                )
                secondsLeft = secondsLeft!! - 1
            } else {
                parent.sendTitlePart(
                    TitlePart.TITLE, Component.text("GO!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                )
                cancelCountdown()
                parent.callEvent(GameStartEvent(parent))
            }
        }
    }

    private fun cancelCountdown() {
        countdown?.cancel()
        countdownRunning = false
        secondsLeft = null
        countdown = null
    }

    override fun deinitialize() {
        countdown?.cancel()
    }
}