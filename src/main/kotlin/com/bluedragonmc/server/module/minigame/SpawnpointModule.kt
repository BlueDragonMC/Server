package com.bluedragonmc.server.module.minigame

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.CircularList
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerRespawnEvent
import net.minestom.server.event.player.PlayerSpawnEvent

/**
 * A module that allows players to spawn in designated locations.
 * A `SpawnpointProvider` is used to determine the spawn location for a specific player.
 * This module does not automatically teleport the player when they join the game. That is the queue's reponsibility.
 */
class SpawnpointModule(val spawnpointProvider: SpawnpointProvider) : GameModule() {

    override val dependencies =
        if (spawnpointProvider is TeamDatabaseSpawnpointProvider) listOf(TeamModule::class) else emptyList()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        spawnpointProvider.initialize(parent)
        logger.info("Initialized spawnpoint provider: ${spawnpointProvider::class.simpleName}")
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.respawnPoint = spawnpointProvider.getSpawnpoint(event.player)
        }
        eventNode.addListener(PlayerRespawnEvent::class.java) { event ->
            event.respawnPosition = spawnpointProvider.getSpawnpoint(event.player)
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
    class TestSpawnpointProvider(vararg spawns: Pos) : SpawnpointProvider {
        private val cachedSpawnpoints = hashMapOf<Player, Pos>()
        private val list = CircularList(listOf(*spawns))
        private var i = 0
        override fun initialize(game: Game) {}

        override fun getSpawnpoint(player: Player): Pos {
            return cachedSpawnpoints.getOrPut(player) { list[i++] }
        }
    }

    /**
     * Spawns all players at a single location.
     */
    class SingleSpawnpointProvider(private val spawn: Pos) : SpawnpointProvider {
        override fun initialize(game: Game) {}

        override fun getSpawnpoint(player: Player): Pos {
            return spawn
        }

    }

    /**
     * Gets spawnpoints from the database.
     */
    class DatabaseSpawnpointProvider(private val allowRandomOrder: Boolean = true) : SpawnpointProvider {

        private val cachedSpawnpoints = hashMapOf<Player, Pos>()
        private lateinit var spawnpoints: CircularList<Pos>
        private var n = 0

        override fun initialize(game: Game) {
            spawnpoints =
                CircularList(if (allowRandomOrder) game.mapData!!.spawnpoints.shuffled() else game.mapData!!.spawnpoints)
        }

        override fun getSpawnpoint(player: Player) = cachedSpawnpoints[player] ?: findSpawnpoint(player)
        private fun findSpawnpoint(player: Player): Pos {
            val pos = spawnpoints[n++]
            if (cachedSpawnpoints.containsValue(pos)) return findSpawnpoint(player) // Prevent players from spawning inside each other
            cachedSpawnpoints[player] = pos
            return pos
        }
    }

    /**
     * Gets spawnpoints from the database.
     * Every team has one spawnpoint. All players spawn at the same location.
     * If a player's spawnpoint is requested, and they are not on a team yet, they are spawned at the first spawnpoint in the database.
     * If they are on a team, they will be given their team's spawnpoint.
     * Requires the [TeamModule] to work properly.
     */
    class TeamDatabaseSpawnpointProvider(
        private val allowRandomOrder: Boolean = false,
    ) : SpawnpointProvider {

        private lateinit var spawnpoints: CircularList<Pos>
        private val cachedSpawnpoints = hashMapOf<TeamModule.Team, Pos>()

        /**
         * Counter for the index in `spawnpoints`, incremented every time a non-cached spawnpoint is requested for a team.
         */
        private var n = 0

        /**
         * Counter for the index in `spawnpoints` that is incremented every time a spawnpoint is requested for a player with no team.
         */
        private var m = 0

        private lateinit var teamModule: TeamModule

        override fun initialize(game: Game) {
            this.teamModule = game.getModule()
            spawnpoints =
                CircularList(if (allowRandomOrder) game.mapData!!.spawnpoints.shuffled() else game.mapData!!.spawnpoints)
        }

        private fun teamOf(player: Player) = teamModule.getTeam(player)
        override fun getSpawnpoint(player: Player) =
            teamOf(player)?.let { cachedSpawnpoints[it] ?: findSpawnpoint(it) } ?: spawnpoints[m++]

        private fun findSpawnpoint(team: TeamModule.Team) = spawnpoints[n++].also { cachedSpawnpoints[team] = it }

    }
}