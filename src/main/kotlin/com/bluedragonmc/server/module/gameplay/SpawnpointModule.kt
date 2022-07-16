package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.MapData
import kotlinx.coroutines.launch
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent

/**
 * A module that allows players to spawn in designated locations.
 * A `SpawnpointProvider` is used to determine the spawn location for a specific player.
 * This module does not automatically teleport the player when they join the game. That is the queue's reponsibility.
 */
class SpawnpointModule(val spawnpointProvider: SpawnpointProvider) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        logger.info("Initializing spawnpoint provider: ${spawnpointProvider::class.simpleName}")
        spawnpointProvider.initialize(parent)
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.respawnPoint = spawnpointProvider.getSpawnpoint(event.player)
        }
    }

    interface SpawnpointProvider {
        /**
         * Called when the spawnpoint module is loaded.
         */
        fun initialize(game: Game)
        /**
         * Returns a spawnpoint for the specified player.
         */
        fun getSpawnpoint(player: Player): Pos
    }

    /**
     * A good spawnpoint provider for testing. Spawns players at the positions provided in the constructor.
     */
    class TestSpawnpointProvider(vararg val spawns: Pos) : SpawnpointProvider {
        override fun initialize(game: Game) {}

        override fun getSpawnpoint(player: Player): Pos {
            return spawns.iterator().next()
        }
    }

    /**
     * Spawns all players at a single location.
     */
    class SingleSpawnpointProvider(val spawn: Pos) : SpawnpointProvider {
        override fun initialize(game: Game) {}

        override fun getSpawnpoint(player: Player): Pos {
            return spawn
        }

    }

    /**
     * Gets spawnpoints from the database.
     */
    class DatabaseSpawnpointProvider(private val allowRandomOrder: Boolean = true, private val callback: () -> Unit) : SpawnpointProvider {
        lateinit var mapData: MapData
        lateinit var iterator: Iterator<Pos>
        private val cachedSpawnpoints = hashMapOf<Player, Pos>()
        override fun initialize(game: Game) {
            DatabaseModule.IO.launch {
                mapData = game.getModule<DatabaseModule>().getMap(game.mapName)
                iterator = if (allowRandomOrder)
                    mapData.spawnpoints.shuffled().iterator()
                else
                    mapData.spawnpoints.iterator()
                callback()
            }
        }

        override fun getSpawnpoint(player: Player): Pos {
            if (cachedSpawnpoints.containsKey(player)) return cachedSpawnpoints[player]!!

            if (!iterator.hasNext()) iterator = if (allowRandomOrder)
                mapData.spawnpoints.shuffled().iterator()
            else
                mapData.spawnpoints.iterator()

            cachedSpawnpoints[player] = iterator.next()
            return getSpawnpoint(player)
        }

    }

    /**
     * Gets spawnpoints from the database.
     * Every team has one spawnpoint. All players spawn at the same location.
     * If a player's spawnpoint is requested and they are not on a team yet, they are spawned at the first spawnpoint in the database.
     * If they are on a team, they will be given their team's spawnpoint.
     * Requires the [TeamModule] to work properly.
     */
    class TeamDatabaseSpawnpointProvider(private val allowRandomOrder: Boolean = false, private val callback: () -> Unit) : SpawnpointProvider {
        private lateinit var game: Game
        lateinit var mapData: MapData
        lateinit var iterator: Iterator<Pos>
        private val cachedSpawnpoints = hashMapOf<TeamModule.Team, Pos>()
        override fun initialize(game: Game) {
            this.game = game
            DatabaseModule.IO.launch {
                mapData = game.getModule<DatabaseModule>().getMap(game.mapName)
                iterator = if (allowRandomOrder)
                    mapData.spawnpoints.shuffled().iterator()
                else
                    mapData.spawnpoints.iterator()
                callback()
            }
        }

        override fun getSpawnpoint(player: Player): Pos {
            val playerTeam = game.getModule<TeamModule>().getTeam(player)
            if (playerTeam != null) {
                if (cachedSpawnpoints.containsKey(playerTeam)) return cachedSpawnpoints[playerTeam]!!

                if (!iterator.hasNext()) iterator = if (allowRandomOrder)
                    mapData.spawnpoints.shuffled().iterator()
                else
                    mapData.spawnpoints.iterator()

                cachedSpawnpoints[playerTeam] = iterator.next()
                return getSpawnpoint(player)
            } else return mapData.spawnpoints[0]
        }

        fun getSpawnpoint(team: TeamModule.Team): Pos {
            if (cachedSpawnpoints.containsKey(team)) return cachedSpawnpoints[team]!!
            if (!iterator.hasNext()) iterator = mapData.spawnpoints.iterator()
            cachedSpawnpoints[team] = iterator.next()
            return getSpawnpoint(team)
        }

    }
}