package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.network.packet.client.login.LoginPluginResponsePacket
import net.minestom.server.network.packet.client.login.LoginStartPacket
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket
import net.minestom.server.network.packet.server.login.LoginPluginRequestPacket
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.network.player.PlayerSocketConnection
import net.minestom.server.tag.Tag
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object InitialInstanceRouter : Bootstrap(EnvType.PRODUCTION) {

    private const val BLUEDRAGON_GET_DEST_CHANNEL = "bluedragonmc:get_dest"

    private val INITIAL_GAME_TAG = Tag.String("initial_game_id")

    private val NO_WORLD_FOUND =
        Component.text("Couldn't find which world to put you in! (No world obtained from handshake)",
            NamedTextColor.RED)
    private val INVALID_WORLD =
        Component.text("Couldn't find which world to put you in! (Invalid world name)", NamedTextColor.RED)
    private val HANDSHAKE_FAILED =
        Component.text("Couldn't find which world to put you in! (Handshake failed)", NamedTextColor.RED)
    private val DATA_LOAD_FAILED =
        Component.text("Failed to load your player data!", NamedTextColor.RED)

    override fun hook(eventNode: EventNode<Event>) {
        MinecraftServer.getPacketListenerManager()
            .setListener(LoginStartPacket::class.java, ::handleLoginStart)
        MinecraftServer.getPacketListenerManager()
            .setListener(LoginPluginResponsePacket::class.java, ::handleLoginPluginMessage)
        MinecraftServer.getGlobalEventHandler()
            .addListener(PlayerLoginEvent::class.java, ::handlePlayerLogin)
    }

    private fun handleLoginStart(packet: LoginStartPacket, connection: PlayerConnection) {
        if (connection !is PlayerSocketConnection) return
        logger.info("Player login start: ${packet.username}")
        // Send a request to Velocity to confirm the player's spawning instance
        val messageId = ThreadLocalRandom.current().nextInt()
        connection.addPluginRequestEntry(messageId, BLUEDRAGON_GET_DEST_CHANNEL)
        connection.sendPacket(LoginPluginRequestPacket(messageId, BLUEDRAGON_GET_DEST_CHANNEL, byteArrayOf())) // send to proxy
        // Use Minestom's handler to process the packet, which will send a request to Velocity for the player's UUID, username, skin, etc.
        packet.process(connection)
    }

    private fun handleLoginPluginMessage(packet: LoginPluginResponsePacket, connection: PlayerConnection) {
        // `packet` is received from proxy
        if (connection !is PlayerSocketConnection) return
        val channel = connection.getPluginRequestChannel(packet.messageId)
        logger.debug("Login plugin response for ${connection.player?.username ?: connection.loginUsername}: channel='$channel', packet=$packet")
        if (channel == BLUEDRAGON_GET_DEST_CHANNEL) {
            handleInstanceDestination(packet, connection)
        } else {
            packet.process(connection)
        }
    }

    private fun handleInstanceDestination(packet: LoginPluginResponsePacket, connection: PlayerSocketConnection) {
        // Update the player's spawning instance, which was received from Velocity
        val gameId = String(packet.data)
        connection.player!!.setTag(INITIAL_GAME_TAG, gameId)
        val username = connection.loginUsername!!
        if (Game.findGame(gameId) == null) {
            logger.warn("Disconnecting player during login phase - desired game ($gameId) not found")
            connection.sendPacket(LoginDisconnectPacket(INVALID_WORLD))
            connection.disconnect()
            return
        }
        logger.debug("Routing player player $username to game '$gameId'...")
    }

    private fun handlePlayerLogin(event: PlayerLoginEvent) {
        // Load the player document synchronously, with a maximum blocking time of 3 seconds
        val cf = CompletableFuture<Unit>()
        cf.completeAsync {
            Database.connection.loadDataDocument(event.player as CustomPlayer)
        }
        try {
            cf.orTimeout(3, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            e.printStackTrace()
            event.player.kick(DATA_LOAD_FAILED)
        }
        // Check if the player's spawning instance was retrieved from Velocity
        if (!event.player.hasTag(INITIAL_GAME_TAG)) {
            // If there is no entry for the player, the handshake must have failed.
            event.player.kick(HANDSHAKE_FAILED)
            return
        }
        val game = Game.findGame(event.player.getTag(INITIAL_GAME_TAG)!!)
        val instance = game?.getModule<InstanceModule>()?.getSpawningInstance(event.player)
        if (instance == null) {
            // If the instance was not set or doesn't exist, disconnect the player.
            logger.warn("No instance found for ${event.player.username} to join!")
            event.player.kick(NO_WORLD_FOUND)
            return
        }
        // If the instance exists, set the player's spawning instance and allow them to connect.
        logger.info("Spawning player ${event.player.username} in game '${game.id}' and instance '${instance.uniqueId}'")
        event.setSpawningInstance(instance)

        MinecraftServer.getSchedulerManager().scheduleNextTick {
            event.player.sendMessage(Component.translatable("global.instance.placing",
                NamedTextColor.GRAY,
                Component.text(game.id + "/" + instance.uniqueId.toString())))
            game.addPlayer(event.player, sendPlayer = false)
            Messaging.IO.launch {
                Messaging.outgoing.playerTransfer(event.player, game.id)
            }
        }
    }
}