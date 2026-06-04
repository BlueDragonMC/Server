package com.bluedragonmc.server.bootstrap.dev

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.bootstrap.prod.InitialInstanceRouter.DATA_LOAD_FAILED
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.game.GameData
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.queue.GameLoader
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Maps
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import java.io.File

object DevInstanceRouter : Bootstrap(EnvType.DEVELOPMENT) {

    lateinit var lobby: Game

    override fun hook(eventNode: EventNode<Event>) {

        val worldsFolder = File("worlds/${Environment.defaultGameName}").listFiles().first()
        lobby = GameLoader.createNewGame(
            GameData(
                Environment.defaultGameName,
                Maps.MapSource(worldsFolder.name, "file://${worldsFolder.absolutePath}", CommonTypes.MapFormat.ANVIL, File(worldsFolder, "config.yml").readText())
            )
        )

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

            // Send the player to the lobby
            event.spawningInstance = lobby.getInstance()
            val spawnpoint =
                lobby.getModuleOrNull<SpawnpointModule>()?.spawnpointProvider?.getSpawnpoint(event.player)
            if (spawnpoint != null) {
                event.player.respawnPoint = spawnpoint
            }
        }
    }
}