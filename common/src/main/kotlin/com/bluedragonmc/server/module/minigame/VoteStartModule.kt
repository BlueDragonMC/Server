package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.*
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.manage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.time.Duration

/**
 * Gives each player a full hotbar of items they can click to vote to start the game.
 * When the majority of players vote to start, the countdown starts.
 */
class VoteStartModule(
    private val minPlayers: Int = 2,
    private val countdownSeconds: Int = 5,
) : GameModule() {

    private val voteStartItem = ItemStack.of(Material.GREEN_CONCRETE).with(
        DataComponents.ITEM_NAME,
        Component.translatable("module.votestart.item.vote.name", NamedTextColor.GREEN, Component.keybind("key.use", NamedTextColor.GRAY))
    )
    private val cancelVoteItem = ItemStack.of(Material.RED_CONCRETE).with(
        DataComponents.ITEM_NAME,
        Component.translatable("module.votestart.item.cancel.name", NamedTextColor.RED, Component.keybind("key.use", NamedTextColor.GRAY))
    )

    private var votes = mutableListOf<Player>()

    private lateinit var parent: Game

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(PlayerJoinGameEvent::class.java) { event ->
            fill(event.player, voteStartItem)
        }
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.hand != PlayerHand.MAIN) return@addListener

            when (event.itemStack) {
                voteStartItem -> {
                    votes += event.player
                    fill(event.player, cancelVoteItem)
                }

                cancelVoteItem -> {
                    votes -= event.player
                    fill(event.player, voteStartItem)
                }

                else -> {}
            }
            event.isCancelled = true
            update()
        }
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            votes.remove(event.player)
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                update()
            }
        }
        MinecraftServer.getSchedulerManager().buildTask {
            if (countdown != null) {
                if (countdown!! > 0) {
                    parent.showTitle(
                        Title.title(
                            Component.text(countdown!!, BRAND_COLOR_PRIMARY_2),
                            Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO)
                        )
                    )
                    parent.callEvent(CountdownEvent.CountdownTickEvent(parent, countdown!!))
                } else {
                    parent.sendTitlePart(
                        TitlePart.TITLE,
                        Component.translatable("module.countdown.go", NamedTextColor.GREEN)
                            .decorate(TextDecoration.BOLD)
                    )
                    countdown = null
                    cancelCountdown()
                    clearPlayerInventories()
                    parent.callEvent(GameStartEvent(parent))
                    parent.state = GameState.INGAME
                    votes.clear()
                }

                countdown = countdown?.minus(1)
            }
        }.repeat(Duration.ofSeconds(1)).schedule().manage(parent)
        eventNode.addListener(GameStateChangedEvent::class.java) { event ->
            if (event.newState != GameState.WAITING && event.newState != GameState.STARTING) {
                clearPlayerInventories()
            }
        }
    }

    private fun fill(player: Player, item: ItemStack) {
        for (i in 0..8) {
            player.inventory.setItemStack(i, item)
        }
    }

    private fun clearPlayerInventories() {
        for (player in parent.players) {
            for ((index, stack) in player.inventory.itemStacks.withIndex()) {
                if (stack == voteStartItem || stack == cancelVoteItem) {
                    player.inventory.setItemStack(index, ItemStack.AIR)
                }
            }
        }
    }

    private var countdown: Int? = null

    private fun update() {
        if (parent.players.size >= minPlayers && votes.size >= parent.players.size / 2f) {
            startCountdown()
        } else if (countdown != null) {
            cancelCountdown()
        }
    }

    private fun startCountdown() {
        parent.callCancellable(CountdownEvent.CountdownStartEvent(parent)) {
            if (countdown == null) {
                countdown = countdownSeconds
            }
            parent.state = GameState.STARTING
        }
    }

    private fun cancelCountdown() {
        if (countdown != null) {
            parent.showTitle(
                Title.title(
                    Component.translatable("module.countdown.cancelled", NamedTextColor.RED),
                    Component.translatable("module.votestart.cancelled.subtitle", NamedTextColor.RED),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(1))
                )
            )
        }
        countdown = null
        if (parent.state == GameState.STARTING) {
            parent.state = GameState.WAITING
        }
    }

    fun hasVoted(player: Player) = votes.contains(player)
}