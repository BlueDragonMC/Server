package com.bluedragonmc.server

import com.bluedragonmc.server.command.InstanceCommand
import com.bluedragonmc.server.game.TeamDeathmatchGame
import com.bluedragonmc.server.module.gameplay.SpawnpointModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.extras.lan.OpenToLAN

fun main() {
    val minecraftServer = MinecraftServer.init()

    // Create a test instance
    val wackyMaze = TeamDeathmatchGame()


    // Make players spawn in the test instance
    MinecraftServer.getGlobalEventHandler().addListener(PlayerLoginEvent::class.java) { event ->
        if (wackyMaze.hasModule<SpawnpointModule>()) event.player.respawnPoint = wackyMaze.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(event.player)
        else event.player.respawnPoint = Pos(0.0, 65.0, 0.0)
        event.setSpawningInstance(wackyMaze.getInstance())
    }

    // Initialize commands
    listOf(
        InstanceCommand("instance", "/instance <list|add|remove> ...", "in")
    ).forEach(MinecraftServer.getCommandManager()::register)

    // Set a custom player provider, so we can easily add fields to the Player class
    MinecraftServer.getConnectionManager().setPlayerProvider(::CustomPlayer)

    // Start the server & bind to port 25565
    minecraftServer.start("0.0.0.0", 25565)

    OpenToLAN.open()

}
