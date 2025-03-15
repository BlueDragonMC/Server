package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.listenSuspend
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent

object InitialInstanceRouter : Bootstrap(EnvType.PRODUCTION) {

    private val INVALID_WORLD =
        Component.text("Couldn't find which world to put you in! (Invalid world name)", NamedTextColor.RED)
    private val INSTANCE_NOT_REGISTERED =
        Component.text("Couldn't find which world to put you in! (Destination not ready)", NamedTextColor.RED)
    private val HANDSHAKE_FAILED =
        Component.text("Couldn't find which world to put you in! (Handshake failed)", NamedTextColor.RED)
    internal val DATA_LOAD_FAILED =
        Component.text("Failed to load your player data!", NamedTextColor.RED)

    override fun hook(eventNode: EventNode<Event>) {
        MinecraftServer.getGlobalEventHandler().listenSuspend<AsyncPlayerConfigurationEvent> { event ->
            if (!event.isFirstConfig) {
                // We only want to handle player logins here
                return@listenSuspend
            }

            val dataLoadJob = Database.IO.async {
                // Load player data from the database
                withTimeout(5000) {
                    Database.connection.loadDataDocument(event.player as CustomPlayer)
                }
            }

            val getDestinationJob = Messaging.IO.async {
                // Find the game that the player requested to join
                withTimeout(5000) {
                    Messaging.outgoing.getDestination(event.player.uuid)
                }
            }

            // After starting both jobs, wait for them to complete

            try {
                dataLoadJob.join()
            } catch (e: Exception) {
                e.printStackTrace()
                event.player.kick(DATA_LOAD_FAILED)
                return@listenSuspend
            }

            val destination = try {
                getDestinationJob.await()
            } catch (e: Exception) {
                e.printStackTrace()
                event.player.kick(HANDSHAKE_FAILED)
                return@listenSuspend
            }

            // Using the destination string, find the player's desired game

            val game = if (!destination.isNullOrBlank()) {
                Game.findGame(destination)
            } else {
                logger.warn("Invalid destination ('$destination') supplied for player ${event.player.username}, sending to Lobby.")
                // If no destination was found, send the player to a lobby.
                Game.games.find { it.name.equals(Environment.defaultGameName, ignoreCase = true) }
            }

            // Spawn the player in the game's spawning instance

            val instance = game?.getModule<InstanceModule>()?.getSpawningInstance(event.player)
            if (instance == null) {
                // If the instance was not set or doesn't exist, disconnect the player.
                logger.warn("No instance found for ${event.player.username} to join!")
                event.player.kick(INVALID_WORLD)
                return@listenSuspend
            }

            if (!instance.isRegistered) {
                logger.warn("Tried to send ${event.player.username} to an unregistered instance!")
                event.player.kick(INSTANCE_NOT_REGISTERED)
                return@listenSuspend
            }

            logger.info("Spawning player ${event.player.username} in game '${game.id}' and instance '${instance.uuid}'")
            event.spawningInstance = instance

            if (game.hasModule<SpawnpointModule>()) {
                // Force the player to spawn at their spawnpoint
                event.player.respawnPoint =
                    game.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(event.player)
            }

            MinecraftServer.getSchedulerManager().scheduleNextTick {
                game.addPlayer(event.player, sendPlayer = false)
            }

            Messaging.IO.launch {
                Messaging.outgoing.playerTransfer(event.player, game.id)
            }
        }
    }
}