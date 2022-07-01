package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent

class SpawnpointModule(val spawnpointProvider: SpawnpointProvider) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.respawnPoint = spawnpointProvider.getSpawnpoint(event.player)
            event.player.respawn()
        }
    }

    @FunctionalInterface
    interface SpawnpointProvider {
        fun getSpawnpoint(player: Player): Pos
    }

    class TestSpawnpointProvider(vararg val spawns: Pos) : SpawnpointProvider {
        override fun getSpawnpoint(player: Player): Pos {
            return spawns.iterator().next()
        }

    }
}