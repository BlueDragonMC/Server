package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
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
            if (threshold > 0 && parent.players.size >= threshold && countdown == null) {
                countdown = createCountdownTask(parent, 10)
            }
        }
        eventNode.addListener(RemoveEntityFromInstanceEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener
            parent.players.remove(event.entity) // todo this is temporary, this should really be in the Game class
            if (threshold > 0 && countdown != null && parent.players.size < threshold) {
                // Stop the countdown
                countdownRunning = false
                countdown?.cancel()
                parent.showTitle(
                    Title.title(
                        Component.text("Cancelled!", NamedTextColor.RED),
                        Component.text(
                            "Not enough players to start (${parent.players.size}/$threshold)",
                            NamedTextColor.RED
                        ),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(1))
                    )
                )
            }
        }
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            event.isCancelled =
                countdownRunning && // Countdown started
                        !countdownEnded && // Countdown not ended
                        !allowMoveDuringCountdown &&
                        (event.newPosition.x != event.player.position.x || event.newPosition.z != event.player.position.z)
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
                        Component.text(seconds, NamedTextColor.DARK_AQUA),
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
                for (module in useOnStart) parent.use(module)
                parent.callEvent(GameStartEvent(parent))
                countdownEnded = true
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