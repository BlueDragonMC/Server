package com.bluedragonmc.server

import com.bluedragonmc.server.command.*
import com.bluedragonmc.server.game.Lobby
import com.bluedragonmc.server.module.gameplay.SpawnpointModule
import com.bluedragonmc.server.queue.TestQueue
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.extras.lan.OpenToLAN

val queue = TestQueue()
lateinit var lobby: Game

fun main() {
    val minecraftServer = MinecraftServer.init()

    // Create a test instance
    lobby = Lobby()

    // Make players spawn in the test instance
    MinecraftServer.getGlobalEventHandler().addListener(PlayerLoginEvent::class.java) { event ->
        event.player.respawnPoint = lobby.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(event.player)
        event.setSpawningInstance(lobby.getInstance())
    }

    // Initialize commands
    listOf(
        InstanceCommand("instance", "/instance <list|add|remove> ...", "in"),
        GameCommand("game", "/game <start|end>"),
        LobbyCommand("lobby", "/lobby", "l", "hub"),
        TeleportCommand("tp", "/tp <player> | /tp <x> <y> <z>"),
        FlyCommand("fly"),
        GameModeCommand("gamemode", "/gamemode <survival|creative|adventure|spectator> [player]", "gm")
    ).forEach(MinecraftServer.getCommandManager()::register)

    // Set a custom player provider, so we can easily add fields to the Player class
    MinecraftServer.getConnectionManager().setPlayerProvider(::CustomPlayer)

    // Start the queue loop, which runs every 2 seconds and handles the players in queue
    queue.start()

    // Start the server & bind to port 25565
    minecraftServer.start("0.0.0.0", 25565)

    OpenToLAN.open()

}
