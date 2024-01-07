package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.model.EventLog
import com.bluedragonmc.server.model.Severity
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent
import net.minestom.server.event.player.PlayerLoginEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object InitialInstanceRouter : Bootstrap(EnvType.PRODUCTION) {

    private val INVALID_WORLD =
        Component.text("Couldn't find which world to put you in! (Invalid world name)", NamedTextColor.RED)
    private val HANDSHAKE_FAILED =
        Component.text("Couldn't find which world to put you in! (Handshake failed)", NamedTextColor.RED)
    private val DATA_LOAD_FAILED =
        Component.text("Failed to load your player data!", NamedTextColor.RED)

    override fun hook(eventNode: EventNode<Event>) {
        MinecraftServer.getGlobalEventHandler()
            .addListener(PlayerLoginEvent::class.java) { event ->
                try {
                    handlePlayerLogin(event)
                } catch (e: Exception) {
                    e.printStackTrace()
                    event.player.kick(HANDSHAKE_FAILED)
                }
            }
        MinecraftServer.getGlobalEventHandler()
            .addListener(AsyncPlayerPreLoginEvent::class.java) { event ->
                loadData(event.player as CustomPlayer)
            }
    }

    private fun loadData(player: Player) {
        val cf = CompletableFuture<Unit>()
        cf.completeAsync {
            Database.connection.loadDataDocument(player as CustomPlayer)
        }
        try {
            cf.orTimeout(3, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            e.printStackTrace()
            player.kick(DATA_LOAD_FAILED)
        }
    }

    private fun getDestination(player: Player): String? {
        val cf = CompletableFuture<String?>()
        cf.completeAsync {
            runBlocking { Messaging.outgoing.getDestination(player.uuid) }
        }
        try {
            return cf.get(3, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            e.printStackTrace()
            player.kick(HANDSHAKE_FAILED)
            throw e
        }
    }

    private fun handlePlayerLogin(event: PlayerLoginEvent) {
        val destination = getDestination(event.player)
        val game = if (!destination.isNullOrBlank()) {
            Game.findGame(destination)
        } else {
            logger.warn("Invalid destination ('$destination') supplied for player ${event.player.username}, sending to Lobby.")
            // If no destination was found, send the player to a lobby.
            Game.games.find { it.name.equals(Environment.defaultGameName, ignoreCase = true) }
        }
        val instance = game?.getModule<InstanceModule>()?.getSpawningInstance(event.player)
        if (instance == null) {
            // If the instance was not set or doesn't exist, disconnect the player.
            logger.warn("No instance found for ${event.player.username} to join!")
            event.player.kick(INVALID_WORLD)
            return
        }
        // If the instance exists, set the player's spawning instance and allow them to connect.
        logger.info("Spawning player ${event.player.username} in game '${game.id}' and instance '${instance.uniqueId}'")
        event.setSpawningInstance(instance)

        if (game.hasModule<SpawnpointModule>()) {
            event.player.respawnPoint =
                game.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(event.player)
        }

        event.player.sendMessage(
            Component.translatable(
                "global.instance.placing",
                NamedTextColor.GRAY,
                Component.text(game.id + "/" + instance.uniqueId.toString())
            )
        )
        game.addPlayer(event.player, sendPlayer = false)
        Messaging.IO.launch {
            Messaging.outgoing.playerTransfer(event.player, game.id)
        }
        Database.IO.launch {
            Database.connection.logEvent(
                EventLog("player_login", Severity.DEBUG)
                    .withProperty("player_uuid", event.player.uuid.toString())
                    .withProperty("initial_game_id", game.id)
                    .withProperty("given_destination", destination)
            )
        }
    }
}