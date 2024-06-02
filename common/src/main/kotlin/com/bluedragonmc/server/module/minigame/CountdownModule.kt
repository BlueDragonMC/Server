package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.CountdownEvent
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.GameState
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import net.minestom.server.entity.Player
import net.minestom.server.entity.RelativeFlags
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent
import net.minestom.server.event.player.*
import net.minestom.server.event.server.ServerTickMonitorEvent
import net.minestom.server.event.trait.CancellableEvent
import java.time.Duration

class CountdownModule(
    private val threshold: Int,
    private val allowMoveDuringCountdown: Boolean = true,
    private val countdownSeconds: Int = 10,
) : GameModule() {

    private var secondsLeft: Int? = null
    private var countdownRunning: Boolean = false

    fun getTimeLeft() = secondsLeft ?: countdownSeconds

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerSpawnEvent::class.java) {
            if (parent.state == GameState.STARTING || parent.state == GameState.INGAME || parent.state == GameState.ENDING || countdownRunning) return@addListener
            if (threshold > 0 && parent.players.size >= threshold) {
                startCountdown(parent)
                parent.state = GameState.STARTING
            }
        }

        val inCountdownEventNode = EventNode.event("in-countdown", EventFilter.ALL) { countdownRunning }
        eventNode.addChild(inCountdownEventNode)

        val handler = { event: CancellableEvent -> event.isCancelled = true }
        inCountdownEventNode.addListener(PlayerBlockBreakEvent::class.java, handler)
        inCountdownEventNode.addListener(PlayerBlockPlaceEvent::class.java, handler)
        inCountdownEventNode.addListener(PlayerBlockInteractEvent::class.java, handler)

        eventNode.addListener(RemoveEntityFromInstanceEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener
            if (threshold > 0 && countdownRunning && parent.players.size < threshold) {
                // Stop the countdown
                cancelCountdown()
                parent.showTitle(
                    Title.title(
                        Component.translatable("module.countdown.cancelled", NamedTextColor.RED),
                        Component.translatable(
                            "module.countdown.cancelled.subtitle",
                            NamedTextColor.RED,
                            Component.text(parent.players.size),
                            Component.text(threshold)
                        ),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(1))
                    )
                )
                parent.state = GameState.WAITING
            }
        }
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (countdownRunning && !allowMoveDuringCountdown) {
                val spawnpoint = parent.getModuleOrNull<SpawnpointModule>()?.spawnpointProvider?.getSpawnpoint(event.player)
                if (spawnpoint == null) {
                    event.isCancelled = true
                    return@addListener
                }
                // Revert the player's position without forcing the player's facing direction
                event.player.teleport(spawnpoint.withView(0f, 0f), null, RelativeFlags.VIEW)
            }
        }

        eventNode.addListener(GameStartEvent::class.java) {
            cancelCountdown()
            parent.state = GameState.INGAME
            parent.players.forEach { player ->
                player.synchronizeNextTick()
            }
        }

        var ticks = 0
        eventNode.addListener(ServerTickMonitorEvent::class.java) {
            if (countdownRunning) {
                ticks++
            } else {
                ticks = 0
            }

            if (countdownRunning && ticks % 20 == 0) { // Every second that the countdown is running
                val seconds = secondsLeft ?: run {
                    cancelCountdown()
                    return@addListener
                }
                if (seconds > 0) {
                    parent.showTitle(
                        Title.title(
                            Component.text(seconds, BRAND_COLOR_PRIMARY_2),
                            Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO)
                        )
                    )
                    parent.callEvent(CountdownEvent.CountdownTickEvent(parent, secondsLeft!!))
                    secondsLeft = secondsLeft!! - 1
                } else {
                    parent.sendTitlePart(
                        TitlePart.TITLE,
                        Component.translatable("module.countdown.go", NamedTextColor.GREEN)
                            .decorate(TextDecoration.BOLD)
                    )
                    parent.callEvent(GameStartEvent(parent))
                }
            }
        }
    }

    private fun startCountdown(parent: Game) {
        cancelCountdown()
        parent.callEvent(CountdownEvent.CountdownStartEvent(parent))
        if (!allowMoveDuringCountdown) parent.players.filter { it.isActive }.forEach { it.teleport(it.respawnPoint) }
        secondsLeft = countdownSeconds
        countdownRunning = true
    }

    private fun cancelCountdown() {
        countdownRunning = false
        secondsLeft = countdownSeconds
    }

    override fun deinitialize() {
        cancelCountdown()
    }
}