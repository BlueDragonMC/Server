package com.bluedragonmc.server

import com.bluedragonmc.server.game.WackyMazeGame
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.PlayerLoginEvent

fun main() {
    val minecraftServer = MinecraftServer.init()

    // Create a test instance
    val wackyMaze = WackyMazeGame()


    // Make players spawn in the test instance
    MinecraftServer.getGlobalEventHandler().addListener(PlayerLoginEvent::class.java) { event ->
        event.player.respawnPoint = Pos(0.0, 65.0, 0.0)
        event.setSpawningInstance(wackyMaze.getInstance())
    }

    // Start the server & bind to port 25565
    minecraftServer.start("0.0.0.0", 25565)

}
