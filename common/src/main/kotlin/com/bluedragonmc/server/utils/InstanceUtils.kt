package com.bluedragonmc.server.utils

import com.bluedragonmc.api.grpc.CommonTypes.GameType.GameTypeFieldSelector
import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.event.EventNode
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture

object InstanceUtils {

    /**
     * Forcefully removes all players from the instance
     * and unregisters it, sending all players to a lobby.
     * @return a CompletableFuture when all players are removed and the instance is unregistered.
     */
    fun forceUnregisterInstance(instance: Instance): CompletableFuture<Void> {
        val eventNode = EventNode.all("temp-vacate-${UUID.randomUUID()}")
        eventNode.addListener(PlayerEvent::class.java) { event ->
            if (event.player.instance == instance) {
                event.player.kick(Component.text("This instance is shutting down."))
                event.player.remove()
            }
        }
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        return vacateInstance(instance).thenRun {
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                MinecraftServer.getInstanceManager().unregisterInstance(instance)
                MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
            }
        }
    }

    /**
     * Send all players in the instance to a lobby.
     * @return a [CompletableFuture] which is completed when all players are removed from the instance.
     */
    fun vacateInstance(instance: Instance): CompletableFuture<Void> {
        if (instance.players.isEmpty()) {
            // If the instance is already empty, return immediately
            return CompletableFuture.completedFuture(null)
        } else {
            // If the instance is not empty, attempt to send all players to a lobby
            val lobby = Game.games.find { it.name == Environment.defaultGameName }
            if (lobby != null) {
                val lobbyInstanceOf = lobby.getModule<InstanceModule>()::getSpawningInstance
                val spawnpointOf = lobby.getModule<SpawnpointModule>().spawnpointProvider::getSpawnpoint
                return CompletableFuture.allOf(
                    *instance.players
                        .map { it.setInstance(lobbyInstanceOf(it), spawnpointOf(it)) }
                        .toTypedArray()
                )
            } else {
                // If there's no lobby, repeatedly queue players for a lobby on another server
                // until every player has disconnected
                val future = CompletableFuture<Void>()
                var completionTask: Task? = null
                val queueTask: Task = MinecraftServer.getSchedulerManager().buildTask {
                    instance.players.forEach {
                        Environment.queue.queue(it, gameType {
                            name = Environment.defaultGameName
                            selectors += GameTypeFieldSelector.GAME_NAME
                        })
                    }
                }.repeat(Duration.ofSeconds(10)).schedule()
                completionTask = MinecraftServer.getSchedulerManager().buildTask {
                    if (instance.players.isEmpty()) {
                        future.complete(null)
                        completionTask?.cancel()
                        queueTask.cancel()
                    }
                }.repeat(Duration.ofSeconds(1)).schedule()
                return future
            }
        }
    }
}