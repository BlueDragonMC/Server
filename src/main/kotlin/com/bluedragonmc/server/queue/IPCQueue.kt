package com.bluedragonmc.server.queue

import com.bluedragonmc.messages.*
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.gameplay.SpawnpointModule
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.utils.broadcast
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.random.Random

object IPCQueue : Queue() {
    private val logger = LoggerFactory.getLogger(IPCQueue::class.java)

    private val queuedPlayers = mutableListOf<Player>()

    override fun queue(player: Player, gameType: GameType) {
        player.sendMessage(Component.text("Adding you to the queue...", NamedTextColor.DARK_GRAY))
        if(queuedPlayers.contains(player)) MessagingModule.publish(RequestRemoveFromQueueMessage(player.uuid))
        else MessagingModule.publish(RequestAddToQueueMessage(player.uuid, gameType))
    }

    override fun start() {
        MessagingModule.subscribe(RequestCreateInstanceMessage::class) { message ->
            if (message.containerId == MessagingModule.containerId) {
                val constructor = gameClasses[message.gameType.name] ?: return@subscribe
                val map = message.gameType.mapName ?: randomMap(message.gameType.name) ?: run {
                    logger.error("No map name was specified and a random map was not found. A new instance cannot be created.")
                    return@subscribe
                }
                val game = constructor.invoke(map)
                val instance = game.getInstance()
                broadcast(Component.text("Created instance ${instance.uniqueId} from type ${message.gameType}.", NamedTextColor.DARK_GRAY))
                // The service will soon be notified of this instance's creation
                // once the mandatory MessagingModule is initialized
            }
        }
        MessagingModule.subscribe(SendPlayerToInstanceMessage::class) { message ->
            val instance = MinecraftServer.getInstanceManager().getInstance(message.instance) ?: return@subscribe
            val player = MessagingModule.findPlayer(message.player) ?: return@subscribe
            player.sendMessage(Component.text("Sending you to ${message.instance}...", NamedTextColor.DARK_GRAY))
            val game = Game.findGame(instance.uniqueId) ?: run {
                player.sendMessage(Component.text("There was an error sending you to the instance! (No game found)", NamedTextColor.RED))
                return@subscribe
            }
            game.players.add(player)
            if (game.hasModule<SpawnpointModule>())
                player.setInstance(instance, game.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(player))
            else player.setInstance(instance)
        }
    }



    fun getMaps(gameType: String): Array<File>? {
        val worldFolder = "worlds/$gameType"
        val file = File(worldFolder)
        if (!(file.exists() && file.isDirectory)) arrayOf<File>()
        return file.listFiles()
    }

    fun getMapNames(gameType: String): ArrayList<String> {
        val maps = getMaps(gameType) ?: return arrayListOf()
        val mapNames = ArrayList<String>()
        for (map in maps) {
            mapNames.add(map.name)
        }
        return mapNames
    }

    fun randomMap(gameType: String): String? {
        val allMaps = getMaps(gameType)
        if (allMaps != null) return allMaps[Random.nextInt(allMaps.size)].name
        return null
    }

    fun randomMapOrDefault(gameType: String, defaultMap: String): String {
        val map = randomMap(gameType)
        if (map == null) return defaultMap
        else return map
    }
}