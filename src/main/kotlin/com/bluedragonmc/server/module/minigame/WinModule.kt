package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_3
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.AwardsModule
import com.bluedragonmc.server.utils.FireworkUtils
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.color.Color
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.item.firework.FireworkEffect
import net.minestom.server.item.firework.FireworkEffectType
import net.minestom.server.item.metadata.FireworkMeta
import java.time.Duration

class WinModule(
    val winCondition: WinCondition = WinCondition.MANUAL,
    private val coinAwardsFunction: (Player, TeamModule.Team) -> Int = { _, _ -> 0 },
) : GameModule() {
    private lateinit var parent: Game

    internal var isWinnerDeclared = false

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(SpectatorModule.StartSpectatingEvent::class.java) {
            if (winCondition == WinCondition.MANUAL) return@addListener
            val spectatorModule =
                parent.getModule<SpectatorModule>() // This module is required for all the win conditions
            if (winCondition == WinCondition.LAST_TEAM_ALIVE) {
                val remainingTeams = parent.getModule<TeamModule>().teams.filter { team ->
                    team.players.any { player -> !spectatorModule.isSpectating(player) }
                }
                if (remainingTeams.size == 1) {
                    declareWinner(remainingTeams.first())
                }
            } else if (winCondition == WinCondition.LAST_PLAYER_ALIVE && parent.players.size - spectatorModule.spectatorCount() <= 1) {
                parent.players.firstOrNull { player -> !spectatorModule.isSpectating(player) }
                    ?.let { declareWinner(it) }
            }
        }
        eventNode.addListener(WinnerDeclaredEvent::class.java) { event ->
            parent.players.forEach { player ->
                val coins = coinAwardsFunction(player, event.winningTeam)
                if (coins == 0) return@forEach
                parent.getModule<AwardsModule>().awardCoins(
                    player, coins, Component.translatable(
                    if (player in event.winningTeam.players) "module.win.coins.won" else "module.win.coins.participation")
                )
            }
        }
    }

    /**
     * Declares the winner of the game to be a specific team, waits 5 seconds, and ends the game.
     * All players are notified of the winning team.
     */
    fun declareWinner(team: TeamModule.Team) {
        if (isWinnerDeclared) return
        MinecraftServer.getGlobalEventHandler().callCancellable(WinnerDeclaredEvent(parent, team)) {
            isWinnerDeclared = true
            // Normal message
            parent.players.forEach {
                it.sendMessage(Component.translatable("module.win.team_won", BRAND_COLOR_PRIMARY_2, team.name)
                    .surroundWithSeparators())
            }
            for (p in parent.players) {
                if (team.players.contains(p)) {
                    p.showTitle(Title.title(Component.translatable("module.win.title.won",
                        NamedTextColor.GOLD,
                        TextDecoration.BOLD), Component.empty()))
                    scheduleWinFireworks(p)
                } else p.showTitle(Title.title(Component.translatable("module.win.title.lost", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.translatable("module.win.subtitle.lost", NamedTextColor.RED)))
            }
            parent.endGame(Duration.ofSeconds(5))
        }
    }

    class WinnerDeclaredEvent(game: Game, val winningTeam: TeamModule.Team) : GameEvent(game)

    fun scheduleWinFireworks(player: Player) {
        var color = player.name.color() ?: BRAND_COLOR_PRIMARY_3
        if (color.asHSV() == NamedTextColor.GRAY.asHSV()) color = BRAND_COLOR_PRIMARY_3
        val instance = parent.getInstance()
        val availablePositions = parent.getModule<SpawnpointModule>().spawnpointProvider.getAllSpawnpoints()
        val fireworkMeta = FireworkMeta.Builder().effects(
                listOf(
                    FireworkEffect(
                        true,
                        true,
                        FireworkEffectType.SMALL_BALL,
                        listOf(Color(color.red(), color.green(), color.blue())),
                        listOf(Color(color.red(), color.green(), color.blue()))
                    )
                )
            )
            // .flightDuration(3)
            .build()
        var delay = 0L
        for (i in 1 .. 3) {
            for (fireworkPosition in availablePositions) {
                MinecraftServer.getSchedulerManager().buildTask {
                    if (player.instance?.uniqueId != instance.uniqueId) return@buildTask
                    FireworkUtils.spawnFirework(player.instance!!, fireworkPosition.add(0.0, 0.0, 0.0), 1500, fireworkMeta)
                }.delay(Duration.ofMillis(delay)).schedule()
                delay += 350
            }
        }
    }

    fun declareWinner(winner: Component) {
        declareWinner(TeamModule.Team(winner, mutableListOf()))
    }

    fun declareWinner(winner: Player) {
        declareWinner(TeamModule.Team(winner.name, mutableListOf(winner)))
    }

    enum class WinCondition {
        /**
         * No automatic win condition. Use the `declareWinner` function to manually declare the winner.
         */
        MANUAL,

        /**
         * Automatically declare the winner as the last non-spectating player. Requires the `SpectatorModule` to be active.
         */
        LAST_PLAYER_ALIVE,

        /**
         * Automatically declares the winner as the last team to have a non-spectating player. Requires the `SpectatorModule` and `TeamModule` to be active.
         */
        LAST_TEAM_ALIVE,

    }

}