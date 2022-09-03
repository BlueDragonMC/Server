package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.mongodb.client.model.Sorts
import com.mongodb.internal.operation.OrderBy
import kotlinx.coroutines.launch
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

    override val dependencies = listOf(DatabaseModule::class)

    companion object {
        // Caches should be static to reduce the number of expensive DB queries
        private val statisticsCache: Cache<String, List<PlayerDocument>> =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build()
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(DataLoadedEvent::class.java) { event ->
            parent.getModuleOrNull<StatisticsModule>()
                ?.recordStatistic(event.player, "times_data_loaded") { i -> i?.plus(1.0) ?: 1.0 }
        }
        if (recordWins) {
            eventNode.addListener(WinModule.WinnerDeclaredEvent::class.java) { event ->
                event.winningTeam.players.forEach { player ->
                    val statName = if (parent.mode == null) "game_${parent.name.lowercase()}_wins"
                    else "game_${parent.name.lowercase()}_${parent.mode.lowercase()}_wins"
                    recordStatistic(player, statName) { value -> value?.plus(1) ?: 1.0 }
                    logger.info("Incremented '$statName' statistic for player ${player.username}.")
                }
            }
        }
    }

    /**
     * Records a statistic for the [player] using the [key] and provided [value].
     */
    fun recordStatistic(player: Player, key: String, value: Double) {
        player as CustomPlayer
        DatabaseModule.IO.launch {
            player.data.compute(PlayerDocument::statistics) { stats -> stats[key] = value; stats }
            logger.info("Recorded statistic '$key' = '$value' for player '${player.username}'.")
        }
    }

    /**
     * Records a statistic for the [player] using the [key] and a [Function] which
     * receives the player's current value for the statistic and returns the new value.
     */
    fun recordStatistic(player: Player, key: String, mapper: Function<Double?, Double>) {
        player as CustomPlayer
        DatabaseModule.IO.launch {
            val newValue = mapper.apply(player.data.statistics[key])
            player.data.compute(PlayerDocument::statistics) { stats -> stats[key] = newValue; stats }
            logger.info("Recorded statistic '$key' = '$newValue' for player '${player.username}'.")
        }
    }

    /**
     * Records a statistic for the [player] using the [key] and provided [value]
     * IF the [predicate], which receives the statistic's current value, returns true.
     */
    fun recordStatistic(player: Player, key: String, value: Double, predicate: Predicate<Double?>) {
        player as CustomPlayer
        DatabaseModule.IO.launch {
            val currentValue = player.data.statistics[key]
            if (predicate.test(currentValue)) {
                player.data.compute(PlayerDocument::statistics) { stats -> stats[key] = value; stats }
                logger.info("Recorded statistic '$key' = '$value' for player '${player.username}'.")
            }
        }
    }

    /**
     * Records a statistic for the [player] using the [key] and computes a new value
     * using the [mapper] [Function], which receives the statistic's current value and
     * must return the new value.
     * The statistic is only recorded if the [predicate], which receives the statistic's
     * current value and new value, returns true.
     */
    fun recordStatistic(
        player: Player,
        key: String,
        mapper: Function<Double?, Double>,
        predicate: BiPredicate<Double?, Double>,
    ) {
        player as CustomPlayer
        DatabaseModule.IO.launch {
            val currentValue = player.data.statistics[key]
            val newValue = mapper.apply(currentValue)
            if (predicate.test(currentValue, newValue)) {
                player.data.compute(PlayerDocument::statistics) { stats -> stats[key] = newValue; stats }
                logger.info("Recorded statistic '$key' = '$newValue' for player '${player.username}'.")
            }
        }
    }

    suspend fun rankPlayersByStatistic(
        key: String,
        sortOrderBy: OrderBy = OrderBy.DESC,
        limit: Int = 10,
    ): Map<PlayerDocument, Double> {

        val cachedEntry = statisticsCache.getIfPresent(sortOrderBy.toString() + key)
        if (cachedEntry != null) return cachedEntry.associateWith { it.statistics[key]!! }

        val sortCriteria = when (sortOrderBy) {
            OrderBy.ASC -> Sorts.ascending("statistics.$key")
            OrderBy.DESC -> Sorts.descending("statistics.$key")
        }

        val documents = DatabaseModule.getPlayersCollection().find().sort(sortCriteria).limit(limit).toList()
            .filter { it.statistics.containsKey(key) }
        statisticsCache.put(sortOrderBy.toString() + key, documents)

        return documents.associateWith { it.statistics[key]!! }
    }
}