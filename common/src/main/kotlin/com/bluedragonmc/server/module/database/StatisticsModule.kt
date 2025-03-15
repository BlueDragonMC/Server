package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.model.PlayerDocument
import com.bluedragonmc.server.model.PlayerRecord
import com.bluedragonmc.server.model.StatisticRecord
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.listenAsync
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiPredicate
import java.util.function.Function
import java.util.function.Predicate

/**
 * A module to save and retrieve players' statistics,
 * as well as rank players by their statistic values.
 *
 * It is recommended, however not required, to use a
 * [StatisticRecorder] to record statistics
 */
class StatisticsModule(private vararg val recorders: StatisticRecorder) : GameModule() {

    companion object {
        // Caches should be static to reduce the number of DB queries
        private val statisticsCache: Cache<String, List<PlayerDocument>> =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).build()

        private lateinit var mostRecentInstance: StatisticsModule

        private val necessaryUpdates = ConcurrentHashMap<CustomPlayer, MutableSet<String>>()

        private val logger = LoggerFactory.getLogger(Companion::class.java)

        private suspend fun commit(player: Player) {
            if (necessaryUpdates.containsKey(player)) {
                player as CustomPlayer
                player.data.update(PlayerDocument::statistics, player.data.statistics)
                necessaryUpdates[player]?.forEach { key ->
                    logger.info("Recorded statistic '$key' = '${player.data.statistics[key]}' for player '${player.username}'.")
                }
                necessaryUpdates.remove(player)
            }
        }

        init {
            MinecraftServer.getGlobalEventHandler().listenAsync<DataLoadedEvent> { event ->
                mostRecentInstance.incrementStatistic(event.player, "times_data_loaded")
            }
            MinecraftServer.getSchedulerManager().buildTask {
                // Commit all queued updates
                Database.IO.launch {
                    ArrayList(necessaryUpdates.keys).forEach { player ->
                        commit(player)
                    }
                }
            }.repeat(Duration.ofMinutes(5)).schedule()
            MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent::class.java) { event ->
                Database.IO.launch {
                    commit(event.player)
                }
            }
        }
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        mostRecentInstance = this

        val ingameOnlyEventNode = EventNode.event("$this-ingame", EventFilter.ALL) { event: Event -> parent.state == GameState.INGAME }
        eventNode.addChild(ingameOnlyEventNode)

        recorders.forEach {
            it.subscribe(this, parent, ingameOnlyEventNode)
        }

        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            Database.IO.launch {
                commit(event.player)
            }
        }
        eventNode.addListener(WinModule.WinnerDeclaredEvent::class.java) { event ->
            event.game.players.forEach { player ->
                Database.IO.launch {
                    commit(player)
                }
            }
        }
    }

    override fun deinitialize() {
        history.clear()
    }

    private val history = mutableMapOf<Pair<Player, String>, Pair<Double?, Double>>()

    fun getHistory(): List<StatisticRecord> {
        return history.map { (playerAndKey, values) ->
            val (player, key) = playerAndKey
            val (oldValue, newValue) = values
            StatisticRecord(
                key = key,
                player = PlayerRecord(uuid = player.uuid, username = player.username),
                oldValue = oldValue,
                newValue = newValue
            )
        }
    }

    /**
     * Records a statistic for the [player] using the [key] and provided [value].
     * The operation may be delayed as statistic updates are batched.
     */
    fun recordStatistic(player: Player, key: String, value: Double) {
        player as CustomPlayer

        // Record the change in the game's statistic history for logging purposes
        if (history.containsKey(player to key)) {
            history[player to key] = history[player to key]?.first to value
        } else {
            history[player to key] = player.data.statistics[key] to value
        }

        // Update the local player data to reflect the change
        player.data.statistics[key] = value

        // Queue a database update operation
        necessaryUpdates.getOrPut(player) { mutableSetOf() }.add(key)
    }

    /**
     * Records a statistic for the [player] using the [key] and a [Function] which
     * receives the player's current value for the statistic and returns the new value.
     */
    fun recordStatistic(player: Player, key: String, mapper: Function<Double?, Double>) {
        player as CustomPlayer
        val newValue = mapper.apply(player.data.statistics[key])
        recordStatistic(player, key, newValue)
    }

    /**
     * Records a statistic for the [player] using the [key] and provided [value]
     * IF the [predicate], which receives the statistic's current value, returns true.
     */
    fun recordStatistic(player: Player, key: String, value: Double, predicate: Predicate<Double?>) {
        player as CustomPlayer
        val currentValue = player.data.statistics[key]
        if (predicate.test(currentValue)) {
            recordStatistic(player, key, value)
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
        val currentValue = player.data.statistics[key]
        val newValue = mapper.apply(currentValue)
        if (predicate.test(currentValue, newValue)) {
            recordStatistic(player, key, newValue)
        }
    }

    /**
     * Increments the statistic for the [player] using the [key]
     * by +1.0. If the statistic does not currently exist for the
     * player, it is recorded as 1.0.
     */
    fun incrementStatistic(
        player: Player,
        key: String,
    ) = recordStatistic(player, key) { current -> current?.plus(1.0) ?: 1.0 }

    /**
     * Records the statistic for the [player] using the [key] if
     * the provided [newValue] is LESS THAN the current stored value.
     * If the statistic is recorded, the [successCallback] is run.
     */
    fun recordStatisticIfLower(player: Player, key: String, newValue: Double, successCallback: Runnable? = null) =
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
    fun recordStatisticIfGreater(player: Player, key: String, newValue: Double, successCallback: Runnable? = null) =
        recordStatistic(player, key, newValue) { old ->
            val record = old == null || newValue > old
            if (record) successCallback?.run()
            record
        }

    enum class OrderBy {
        ASC, DESC
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

    class EventStatisticRecorder<T : Event>(
        private val eventType: Class<T>,
        val handler: suspend StatisticsModule.(Game, T) -> Unit,
    ) : StatisticRecorder() {
        override fun subscribe(module: StatisticsModule, game: Game, eventNode: EventNode<Event>) {
            eventNode.addListener(eventType) { event ->
                Database.IO.launch { handler(module, game, event) }
            }
        }
    }

    class MultiStatisticRecorder(private vararg val recorders: StatisticRecorder) : StatisticRecorder() {
        override fun subscribe(module: StatisticsModule, game: Game, eventNode: EventNode<Event>) {
            recorders.forEach { it.subscribe(module, game, eventNode) }
        }
    }

    abstract class StatisticRecorder {

        abstract fun subscribe(module: StatisticsModule, game: Game, eventNode: EventNode<Event>)
    }
}