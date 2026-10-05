package com.bluedragonmc.server.module.minigame.win

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_3
import com.bluedragonmc.server.ModuleHolder
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.module.*
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.module.minigame.SpectatorModule
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.utils.CircularList
import com.bluedragonmc.server.utils.FireworkUtils
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.color.Color
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.item.component.FireworkExplosion
import net.minestom.server.item.component.FireworkList
import java.time.Duration

/**
 * Handles the win celebration that takes place at the end of every minigame
 * and prints the top 3 players/teams based on some metric.
 *
 * [See Documentation](https://developer.bluedragonmc.com/modules/winmodule/)
 */
@DependsOn(SpawnpointModule::class, GameStateModule::class, PlayerListModule::class)
@SoftDependsOn(SpectatorModule::class, TeamModule::class, GlobalCosmeticModule::class)
class WinModule(
    val winCondition: WinCondition = WinCondition.MANUAL,
    /** How competitors are ordered when the game ends. */
    val ranking: Ranking = Ranking.AUTOMATIC,
    /**
     * When set, the game is automatically won once a competitor reaches this score (see [reportScore]).
     * Every competitor at or above the threshold when the [tieWindow] expires is declared a winner.
     */
    val scoreThreshold: Double? = null,
    /**
     * How close together two events must be (in time) to count as a tie.
     */
    val tieWindow: Duration = Duration.ofMillis(500),
) : GameModule() {
    private lateinit var parent: ModuleHolder

    private var isWinnerDeclared = false
    private var standingsPrinted = false

    /** The winner's display name, used as a fallback announcement when no standings are available. */
    private var winningName: Component? = null

    private val standings: WinStandings = WinStandings(tieWindow) {
        if (winCondition == WinCondition.LAST_TEAM_ALIVE) {
            getModuleOrNull<TeamModule>()?.teams?.map { Competitor.of(it) } ?: emptyList()
        } else {
            players.map { Competitor.of(it) }
        }
    }

    private var survivalCheckScheduled = false
    private var thresholdCheckScheduled = false

    override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(SpectatorModule.StartSpectatingEvent::class.java) { event ->
            if (winCondition != WinCondition.LAST_PLAYER_ALIVE && winCondition != WinCondition.LAST_TEAM_ALIVE) return@addListener
            if (state != GameState.INGAME) return@addListener
            if (scoreThreshold == null) scheduleSurvivalCheck()
            recordSpectatorEliminations(event.player)
        }
        eventNode.addListener(GameStateChangedEvent::class.java) { event ->
            if (event.newState == GameState.ENDING && event.oldState != GameState.ENDING) announceResults()
        }
    }

    override fun deinitialize() {
        standings.clear()
    }

    /** Records the current score of [competitor]. Higher scores rank better under [Ranking.SCORE_DESC]. */
    fun reportScore(competitor: Competitor, value: Double) {
        standings.reportScore(competitor, value)
        maybeScheduleThresholdCheck()
    }

    fun reportScore(team: TeamModule.Team, value: Double) = reportScore(Competitor.of(team), value)
    fun reportScore(player: Player, value: Double) = reportScore(Competitor.of(player), value)

    /** Records that [competitor] was eliminated. Later eliminations rank better under [Ranking.SURVIVAL]. */
    fun reportElimination(competitor: Competitor) = standings.reportElimination(competitor)
    fun reportElimination(team: TeamModule.Team) = reportElimination(Competitor.of(team))
    fun reportElimination(player: Player) = reportElimination(Competitor.of(player))

    /** Clears every recorded score and placement, suppressing the end-of-game top-three message. */
    fun clearStandings() = standings.clear()

    /**
     * Returns the current standings, ordered from best to worst. Competitors sharing a
     * [Standing] are tied. Returns an empty list when no ranking is tracked.
     */
    fun getStandings(): List<Standing> {
        val ranking = when (ranking) {
            Ranking.AUTOMATIC -> when (winCondition) {
                WinCondition.LAST_PLAYER_ALIVE, WinCondition.LAST_TEAM_ALIVE -> Ranking.SURVIVAL
                else -> if (standings.hasScores) Ranking.SCORE_DESC else Ranking.NONE
            }

            else -> ranking
        }

        return standings.compute(ranking)
    }

    /**
     * Declares every competitor in [winners] a winner, waits 5 seconds, and ends the game.
     */
    fun declareWinners(winners: Collection<Competitor>) {
        if (isWinnerDeclared || winners.isEmpty()) return
        val name =
            Component.join(JoinConfiguration.commas(true), winners.map { it.displayName })
        val winningPlayers = winners.flatMap { it.players }.distinct()
        MinecraftServer.getGlobalEventHandler().callCancellable(
            WinnerDeclaredEvent(parent, name, winningPlayers)
        ) {
            isWinnerDeclared = true
            winningName = name
            val isTie = winners.size > 1
            for (p in players) {
                if (winningPlayers.contains(p)) {
                    val title = if (isTie) {
                        Component.translatable("module.win.title.draw", DRAW_COLOR, TextDecoration.BOLD)
                    } else {
                        Component.translatable("module.win.title.won", NamedTextColor.GOLD, TextDecoration.BOLD)
                    }
                    p.showTitle(Title.title(title, Component.empty()))
                    scheduleWinFireworks(p)
                } else p.showTitle(
                    Title.title(
                        Component.translatable("module.win.title.lost", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.translatable("module.win.subtitle.lost", NamedTextColor.RED)
                    )
                )
            }
            // Ending the game fires GameStateChangedEvent, which announces the winner and top three competitors.
            getModule<GameStateModule>().endGameLater(Duration.ofSeconds(5))
        }
    }

    /**
     * Declares the winner of the game to be a specific team, waits 5 seconds, and ends the game.
     * All players are notified of the winning team.
     */
    fun declareWinner(winningTeamName: Component, winningTeamPlayers: Collection<Player>) {
        declareWinners(listOf(Competitor.CustomCompetitor(winningTeamName, winningTeamPlayers)))
    }

    fun declareWinner(team: TeamModule.Team) = declareWinners(listOf(Competitor.of(team)))

    fun declareWinner(winner: Player) = declareWinners(listOf(Competitor.of(winner)))

    class WinnerDeclaredEvent(
        game: ModuleHolder,
        val winningTeamName: Component,
        val winningTeamPlayers: Collection<Player>,
    ) : GameEvent(game)

    private fun recordSpectatorEliminations(player: Player) {
        val spectatorModule = getModuleOrNull<SpectatorModule>() ?: return
        if (winCondition == WinCondition.LAST_PLAYER_ALIVE) {
            reportElimination(player)
            return
        }
        // LAST_TEAM_ALIVE: report a team once it has no remaining non-spectating players
        val team = getModuleOrNull<TeamModule>()?.getTeam(player) ?: return
        if (team.players.none { !spectatorModule.isSpectating(it) }) {
            reportElimination(team)
        }
    }

    /**
     * Defers the survival win check by [tieWindow] so that simultaneous eliminations
     * are grouped into a tie instead of declaring the first survivor the winner.
     */
    private fun scheduleSurvivalCheck() {
        if (survivalCheckScheduled) return
        survivalCheckScheduled = true
        buildTask {
            survivalCheckScheduled = false
            checkSurvivalWin()
        }.delay(tieWindow).schedule()
    }

    private fun checkSurvivalWin() {
        if (isWinnerDeclared || state != GameState.INGAME) return
        if (scoreThreshold != null) return
        if (winCondition != WinCondition.LAST_PLAYER_ALIVE && winCondition != WinCondition.LAST_TEAM_ALIVE) return

        val survivors = standings.survivors()
        if (survivors.size > 1) return
        val topGroup = survivors.ifEmpty { getStandings().firstOrNull()?.competitors.orEmpty() }
        if (topGroup.isNotEmpty()) {
            declareWinners(topGroup)
        } else {
            // No survivors and no eliminations were recorded, so no winner can be determined.
            // This usually means the game is misconfigured (e.g. LAST_TEAM_ALIVE without any teams).
            logger.warn(
                "Survival win check found no competitors to declare as the winner " +
                        "(winCondition=$winCondition). Ending the game without a winner."
            )
            getModule<GameStateModule>().endGameLater(Duration.ofSeconds(3))
        }
    }

    private fun maybeScheduleThresholdCheck() {
        val threshold = scoreThreshold ?: return
        if (thresholdCheckScheduled || isWinnerDeclared) return
        if (standings.competitorsAtOrAbove(threshold).isEmpty()) return
        thresholdCheckScheduled = true
        buildTask {
            thresholdCheckScheduled = false
            if (isWinnerDeclared) return@buildTask
            val winners = standings.competitorsAtOrAbove(threshold)
            if (winners.isNotEmpty()) declareWinners(winners)
        }.delay(tieWindow).schedule()
    }

    /**
     * Announces the top three placements as a single message enclosed by separators.
     * When there are no standings to show, falls back to announcing the winner by name.
     */
    private fun announceResults() {
        if (standingsPrinted) return
        standingsPrinted = true

        val lines = mutableListOf<Component>()
        val standings = buildStandings(getStandings())
        if (standings != null) {
            lines.add(standings)
        } else {
            winningName?.let { name ->
                lines.add(Component.translatable("module.win.team_won", BRAND_COLOR_PRIMARY_2, name))
            }
        }
        if (lines.isEmpty()) return

        val message = Component.join(JoinConfiguration.newlines(), lines)
        players.forEach { it.sendMessage(message.surroundWithSeparators()) }
    }

    /** Builds the top-three lines, or returns null when there are no standings to show. */
    private fun buildStandings(standings: List<Standing>): Component? {
        if (standings.isEmpty()) return null
        val lines = mutableListOf<Component>()
        lines.add(Component.translatable("module.win.results.header", BRAND_COLOR_PRIMARY_2))
        standings.take(3).forEach { standing ->
            standing.competitors.forEach { competitor ->
                lines.add(
                    if (standing.score != null) {
                        Component.translatable(
                            "module.win.results.entry_scored",
                            BRAND_COLOR_PRIMARY_1,
                            Component.text(standing.rank),
                            competitor.displayName,
                            Component.text(formatScore(standing.score))
                        )
                    } else {
                        Component.translatable(
                            "module.win.results.entry",
                            BRAND_COLOR_PRIMARY_1,
                            Component.text(standing.rank),
                            competitor.displayName
                        )
                    }
                )
            }
        }
        return Component.join(JoinConfiguration.newlines(), lines)
    }

    private fun formatScore(score: Double): String =
        if (score % 1.0 == 0.0) score.toInt().toString() else String.format("%.1f", score)

    private val defaultColors = arrayOf(BRAND_COLOR_PRIMARY_1, BRAND_COLOR_PRIMARY_2, BRAND_COLOR_PRIMARY_3)

    private fun scheduleWinFireworks(player: Player) {
        val colors = if (parent.hasModule<GlobalCosmeticModule>()) {
            getModule<GlobalCosmeticModule>().getFireworkColor(player).ifEmpty { defaultColors }
        } else {
            if (player.name.color() != null && player.name.color() != NamedTextColor.GRAY) {
                arrayOf(player.name.color()!!)
            } else {
                defaultColors
            }
        }
        val instance = player.instance ?: return
        val availablePositions = getModule<SpawnpointModule>().spawnpointProvider.getAllSpawnpoints()
        val fireworkMeta = colors.map {
            FireworkList(
                1,
                listOf(
                    FireworkExplosion(
                        FireworkExplosion.Shape.SMALL_BALL,
                        listOf(Color(it.red(), it.green(), it.blue())),
                        listOf(Color(it.red(), it.green(), it.blue())),
                        true, true
                    )
                )
            )
        }
        val circular = CircularList(fireworkMeta)
        var delay = 0L
        repeat(3) {
            availablePositions.forEachIndexed { index, fireworkPosition ->
                buildTask {
                    if (player.instance?.uuid != instance.uuid) return@buildTask
                    FireworkUtils.spawnFirework(
                        this,
                        player.instance!!,
                        fireworkPosition.add(0.0, 0.0, 0.0),
                        1500,
                        circular[index]
                    )
                }.delay(Duration.ofMillis(delay)).schedule()
                delay += 350
            }
        }
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

    companion object {
        /** Light brown / beige, used for the "DRAW!" title when competitors tie. */
        val DRAW_COLOR: TextColor = TextColor.color(0xD2B48C)
    }
}
