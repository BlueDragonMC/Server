package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.packet.PacketUtils
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
import net.minestom.server.event.player.*
import java.time.Duration
import java.util.*
import kotlin.concurrent.fixedRateTimer

class CountdownModule(
    private val threshold: Int,
    val allowMoveDuringCountdown: Boolean = true,
    private val countdownSeconds: Int = 10,
    private vararg val useOnStart: GameModule
) : GameModule() {

    constructor(
        threshold: Int,
        allowMoveDuringCountdown: Boolean = true,
        vararg useOnStart: GameModule
    ) : this(threshold, allowMoveDuringCountdown, 10, *useOnStart)

    private var countdown: Timer? = null
    private var secondsLeft: Int? = null
    private var countdownRunning: Boolean = false
    private var countdownEnded: Boolean = false

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (countdownEnded) return@addListener
            if (threshold > 0 && parent.players.size >= threshold && countdown == null) {
                countdown = createCountdownTask(parent)
                parent.state = GameState.STARTING
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (!countdownEnded) event.isCancelled = true
        }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (!countdownEnded) event.isCancelled = true
        }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (!countdownEnded) event.isCancelled = true
        }

        eventNode.addListener(RemoveEntityFromInstanceEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener
            if (threshold > 0 && countdown != null && parent.players.size < threshold) {
                // Stop the countdown
                cancelCountdown()
                parent.showTitle(
                    Title.title(
                        Component.translatable("module.countdown.cancelled", NamedTextColor.RED),
                        Component.translatable("module.countdown.cancelled.subtitle", NamedTextColor.RED, Component.text(parent.players.size), Component.text(threshold)),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(1))
                    )
                )
                parent.state = GameState.WAITING
            }
        }
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (countdownRunning && // Countdown started
                !countdownEnded && // Countdown not ended
                !allowMoveDuringCountdown
            ) {
                parent.getModuleOrNull<SpawnpointModule>()?.spawnpointProvider?.getSpawnpoint(event.player)?.let { sp ->
                    if(event.newPosition.distanceSquared(sp) > 4.0) {
                        event.isCancelled = true
                        event.player.teleport(sp)
                        return@addListener
                    }
                }
                // Revert the player's position without forcing the player's facing direction
                event.newPosition = event.player.position
                event.player.sendPacket(
                    PacketUtils.getRelativePosLookPacket(event.player, event.player.position)
                )
            }
        }

        eventNode.addListener(GameStartEvent::class.java) { event ->
            cancelCountdown()
            for (module in useOnStart) parent.use(module)
            parent.state = GameState.INGAME
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                countdownEnded = true
            }
            parent.players.forEach { player ->
                player.askSynchronization()
            }
        }
    }

    private fun createCountdownTask(parent: Game): Timer {
        if (!allowMoveDuringCountdown) parent.players.filter { it.isActive }.forEach { it.teleport(it.respawnPoint) }
        secondsLeft = countdownSeconds
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
                    TitlePart.TITLE, Component.translatable("module.countdown.go", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                )
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