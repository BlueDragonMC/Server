package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Database
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.mongodb.internal.operation.OrderBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.time.Duration
import java.util.function.BiPredicate
import java.util.function.Function
import java.util.function.Predicate

/**
 * A module to save and retrieve players' statistics,
 * as well as rank players by their statistic values.
 */
class StatisticsModule(private val recordWins: Boolean = true) : GameModule() {

    companion object {
        // Caches should be static to reduce the number of expensive DB queries
        private val statisticsCache: Cache<String, List<PlayerDocument>> =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build()
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(DataLoadedEvent::class.java) { event ->
            Database.IO.launch {
                parent.getModuleOrNull<StatisticsModule>()
                    ?.recordStatistic(event.player, "times_data_loaded") { i -> i?.plus(1.0) ?: 1.0 }
            }
        }
        if (recordWins) {
            eventNode.addListener(WinModule.WinnerDeclaredEvent::class.java) { event ->
                event.winningTeam.players.forEach { player ->
                    val statName = if (parent.mode == null) "game_${parent.name.lowercase()}_wins"
                    else "game_${parent.name.lowercase()}_${parent.mode.lowercase()}_wins"
                    runBlocking {
                        recordStatistic(player, statName) { value -> value?.plus(1) ?: 1.0 }
                    }
                    logger.info("Incremented '$statName' statistic for player ${player.username}.")
                }
            }
        }
    }

    /**
     * Records a statistic for the [player] using the [key] and provided [value].
     */
    suspend fun recordStatistic(player: Player, key: String, value: Double) {
        player as CustomPlayer
        player.data.compute(PlayerDocument::statistics) { stats -> stats[key] = value; stats }
        logger.info("Recorded statistic '$key' = '$value' for player '${player.username}'.")
    }

    /**
     * Records a statistic for the [player] using the [key] and a [Function] which
     * receives the player's current value for the statistic and returns the new value.
     */
    suspend fun recordStatistic(player: Player, key: String, mapper: Function<Double?, Double>) {
        player as CustomPlayer
        val newValue = mapper.apply(player.data.statistics[key])
        player.data.compute(PlayerDocument::statistics) { stats -> stats[key] = newValue; stats }
        logger.info("Recorded statistic '$key' = '$newValue' for player '${player.username}'.")
    }

    /**
     * Records a statistic for the [player] using the [key] and provided [value]
     * IF the [predicate], which receives the statistic's current value, returns true.
     */
    suspend fun recordStatistic(player: Player, key: String, value: Double, predicate: Predicate<Double?>) {
        player as CustomPlayer
        val currentValue = player.data.statistics[key]
        if (predicate.test(currentValue)) {
            player.data.compute(PlayerDocument::statistics) { stats -> stats[key] = value; stats }
            logger.info("Recorded statistic '$key' = '$value' for player '${player.username}'.")
        }
    }

    /**
     * Records a statistic for the [player] using the [key] and computes a new value
     * using the [mapper] [Function], which receives the statistic's current value and
     * must return the new value.
     * The statistic is only recorded if the [predicate], which receives the statistic's
     * current value and new value, returns true.
     */
    suspend fun recordStatistic(
        player: Player,
        key: String,
        mapper: Function<Double?, Double>,
        predicate: BiPredicate<Double?, Double>,
    ) {
        player as CustomPlayer
        val currentValue = player.data.statistics[key]
        val newValue = mapper.apply(currentValue)
        if (predicate.test(currentValue, newValue)) {
            player.data.compute(PlayerDocument::statistics) { stats -> stats[key] = newValue; stats }
            logger.info("Recorded statistic '$key' = '$newValue' for player '${player.username}'.")
        }
    }

    /**
     * Increments the statistic for the [player] using the [key]
     * by +1.0. If the statistic does not currently exist for the
     * player, it is recorded as 1.0.
     */
    suspend fun incrementStatistic(
        player: Player,
        key: String
    ) = recordStatistic(player, key) { current -> current?.plus(1.0) ?: 1.0 }

    /**
     * Records the statistic for the [player] using the [key] if
     * the provided [newValue] is LESS THAN the current stored value.
     * If the statistic is recorded, the [successCallback] is run.
     */
    suspend fun recordStatisticIfLower(player: Player, key: String, newValue: Double, successCallback: Runnable? = null) =
        recordStatistic(player, key, newValue) { old ->
            val record = old == null || old > newValue
            if (record) successCallback?.run()
            record
        }

    /**
     * Records the statistic for the [player] using the [key] if
     * the provided [newValue] is GREATER THAN the current stored value.
     * If the statistic is recorded, the [successCallback] is run.
     */
    suspend fun recordStatisticIfGreater(player: Player, key: String, newValue: Double, successCallback: Runnable? = null) =
        recordStatistic(player, key, newValue) { old ->
            val record = old == null || newValue > old
            if (record) successCallback?.run()
            record
        }

    suspend fun rankPlayersByStatistic(
        key: String,
        sortOrderBy: OrderBy = OrderBy.DESC,
        limit: Int = 10,
    ): Map<PlayerDocument, Double> {

        val cachedEntry = statisticsCache.getIfPresent(sortOrderBy.toString() + key)
        if (cachedEntry != null) return cachedEntry.associateWith { it.statistics[key]!! }

        val documents = Database.connection.rankPlayersByStatistic(key, sortOrderBy.toString(), limit)
        statisticsCache.put(sortOrderBy.toString() + key, documents)

        return documents.associateWith { it.statistics[key]!! }
    }
}