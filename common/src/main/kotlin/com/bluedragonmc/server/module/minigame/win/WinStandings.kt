package com.bluedragonmc.server.module.minigame.win

import java.time.Duration
import kotlin.math.abs

/**
 * Tracks the end-of-game ordering of a game's competitors.
 */
internal class WinStandings(
    /**
     * How close together two ranking events must be to count as a tie.
     */
    val tieWindow: Duration,
    /**
     * Supplies every competitor that exists in the game, used to determine survivors.
     * Returns teams or individual players depending on the game's win condition.
     */
    private val competitorListProvider: () -> List<Competitor>,
) {
    private data class RankEvent(val competitor: Competitor, val time: Long)

    private val scores = LinkedHashMap<Competitor, Double>()
    private val rankEvents = mutableListOf<RankEvent>()
    private val eliminated = mutableSetOf<Competitor>()

    val hasScores: Boolean get() = scores.isNotEmpty()

    /** Records the current score of [competitor]. */
    fun reportScore(competitor: Competitor, value: Double) {
        scores[competitor] = value
    }

    /** Records that [competitor] was eliminated at the current time. */
    fun reportElimination(competitor: Competitor) {
        if (eliminated.add(competitor)) {
            rankEvents.add(RankEvent(competitor, System.currentTimeMillis()))
        }
    }

    /** Every competitor that has reached or exceeded [threshold], or an empty list if none have. */
    fun competitorsAtOrAbove(threshold: Double): List<Competitor> =
        scores.filterValues { it >= threshold }.keys.toList()

    /** Clears every recorded score and placement. */
    fun clear() {
        scores.clear()
        rankEvents.clear()
        eliminated.clear()
    }

    /**
     * Returns the standings, ordered from best to worst, with ties grouped into a single
     * [Standing]. Returns an empty list for [Ranking.NONE].
     */
    fun compute(ranking: Ranking): List<Standing> = when (ranking) {
        Ranking.SURVIVAL -> survivalStandings()
        Ranking.ELIMINATION_ORDER -> eliminationOrderStandings()
        Ranking.SCORE_DESC -> scoreStandings(descending = true)
        Ranking.SCORE_ASC -> scoreStandings(descending = false)
        Ranking.AUTOMATIC, Ranking.NONE -> emptyList()
    }

    /** Every competitor that has not been eliminated. */
    fun survivors(): List<Competitor> = competitorListProvider().filter { it !in eliminated }

    private fun survivalStandings(): List<Standing> {
        val live = survivors()
        val result = mutableListOf<Standing>()
        var rank = 1
        if (live.isNotEmpty()) {
            result.add(Standing(rank, live))
            rank += live.size
        }
        for (group in groupByWindow(rankEvents.sortedBy { it.time }.reversed())) {
            result.add(Standing(rank, group))
            rank += group.size
        }
        return result
    }

    private fun eliminationOrderStandings(): List<Standing> {
        val result = mutableListOf<Standing>()
        var rank = 1
        for (group in groupByWindow(rankEvents.sortedBy { it.time }.reversed())) {
            result.add(Standing(rank, group))
            rank += group.size
        }
        return result
    }

    private fun scoreStandings(descending: Boolean): List<Standing> {
        val entries = if (descending) {
            scores.entries.sortedByDescending { it.value }
        } else {
            scores.entries.sortedBy { it.value }
        }
        val result = mutableListOf<Standing>()
        var rank = 1
        var index = 0
        while (index < entries.size) {
            val value = entries[index].value
            val group = mutableListOf<Competitor>()
            while (index < entries.size && entries[index].value == value) {
                group.add(entries[index].key)
                index++
            }
            result.add(Standing(rank, group, value))
            rank += group.size
        }
        return result
    }

    /** Groups consecutive events whose timestamps fall within [tieWindow] of the group's first event. */
    private fun groupByWindow(events: List<RankEvent>): List<List<Competitor>> {
        if (events.isEmpty()) return emptyList()
        val window = tieWindow.toMillis()
        val groups = mutableListOf<MutableList<Competitor>>()
        var groupStart = events.first().time
        for (event in events) {
            if (groups.isEmpty() || abs(event.time - groupStart) > window) {
                groups.add(mutableListOf(event.competitor))
                groupStart = event.time
            } else {
                groups.last().add(event.competitor)
            }
        }
        return groups
    }
}
