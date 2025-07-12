package com.bluedragonmc.server.bootstrap.dev

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.bootstrap.prod.InitialInstanceRouter.DATA_LOAD_FAILED
import com.bluedragonmc.server.isLobbyInitialized
import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.utils.listen
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent

object DevInstanceRouter : Bootstrap(EnvType.DEVELOPMENT) {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            // When a player logs in and spawns in the lobby, add them to the lobby's player list
            if (event.isFirstSpawn && event.instance == lobby.getInstance()) {
                lobby.addPlayer(event.player, sendPlayer = false)
            }
        }
        eventNode.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            try {
                runBlocking {
                    withTimeout(5000) {
                        Database.connection.loadDataDocument(event.player as CustomPlayer)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                event.player.kick(DATA_LOAD_FAILED)
            }

            if (isLobbyInitialized()) {
                // Send the player to the lobby
                event.spawningInstance = lobby.getInstance()
                val spawnpoint =
                    lobby.getModuleOrNull<SpawnpointModule>()?.spawnpointProvider?.getSpawnpoint(event.player)
                if (spawnpoint != null) {
                    event.player.respawnPoint = spawnpoint
                }
            } else {
                // Send the player to a temporary "limbo" instance while the lobby is being loaded
                val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
                instance.enableAutoChunkLoad(false)
                instance.eventNode().listen<InstanceTickEvent> {
                    if (instance.players.isEmpty()) {
                        MinecraftServer.getInstanceManager().unregisterInstance(instance)
                    }
                    if (isLobbyInitialized()) {
                        lobby.addPlayer(event.player)
                    }
                }
                event.spawningInstance = instance
            }
        }
    }
}