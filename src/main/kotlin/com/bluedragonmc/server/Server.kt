package com.bluedragonmc.server

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.instance.block.Block

fun main() {
    val minecraftServer = MinecraftServer.init()

    val instanceManager = MinecraftServer.getInstanceManager()

    // Create a test instance
    val testContainer = instanceManager.createInstanceContainer()
    val testInstance = instanceManager.createSharedInstance(testContainer)
    testInstance.setGenerator { unit ->
        unit.modifier().fillHeight(0, 64, Block.STONE)
    }

    // Make players spawn in the test instance
    MinecraftServer.getGlobalEventHandler().addListener(PlayerLoginEvent::class.java) { event ->
        event.player.respawnPoint = Pos(0.0, 65.0, 0.0)
        event.setSpawningInstance(testInstance)
    }

    // Start the server & bind to port 25565
    minecraftServer.start("0.0.0.0", 25565)

}
