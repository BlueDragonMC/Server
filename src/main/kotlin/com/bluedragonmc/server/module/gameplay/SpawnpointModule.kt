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
 */
class SpawnpointModule(val spawnpointProvider: SpawnpointProvider) : GameModule() {

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        logger.info("Initializing spawnpoint provider: ${spawnpointProvider::class.simpleName}")
        spawnpointProvider.initialize(parent)
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            logger.info("Finding spawnpoint for player ${event.player.username} using provider ${spawnpointProvider::class.simpleName}")
            event.player.respawnPoint = spawnpointProvider.getSpawnpoint(event.player)
            event.player.respawn()
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
     * Gets spawnpoints from the database.
     */
    class DatabaseSpawnpointProvider(): SpawnpointProvider {
        lateinit var mapData: MapData
        override fun initialize(game: Game) {
            DatabaseModule.IO.launch {
                mapData = game.getModule<DatabaseModule>().getMap(game.mapName)
            }
        }

        override fun getSpawnpoint(player: Player): Pos {
            return mapData.spawnpoints.iterator().next()
        }

    }
}