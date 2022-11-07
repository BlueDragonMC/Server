package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.api.grpc.playerTransferRequest
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.queue.ProductionEnvironment
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.extras.velocity.VelocityProxy
import net.minestom.server.instance.Instance
import net.minestom.server.listener.LoginStartListener
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.client.login.LoginPluginResponsePacket
import net.minestom.server.network.packet.client.login.LoginStartPacket
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket
import net.minestom.server.network.packet.server.login.LoginPluginRequestPacket
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.network.player.PlayerSocketConnection
import net.minestom.server.utils.binary.BinaryReader
import java.net.InetSocketAddress
import java.util.concurrent.ThreadLocalRandom

object InitialInstanceRouter : Bootstrap(ProductionEnvironment::class) {

    private const val BLUEDRAGON_GET_DEST_CHANNEL = "bluedragonmc:get_dest"
    private const val VELOCITY_PLAYER_INFO_CHANNEL = VelocityProxy.PLAYER_INFO_CHANNEL

    private val INVALID_PROXY_RESPONSE =
        Component.text("Your session could not be validated! (Invalid proxy response)", NamedTextColor.RED)
    private val NO_WORLD_FOUND =
        Component.text("Couldn't find which world to put you in! (No world obtained from handshake)",
            NamedTextColor.RED)
    private val INVALID_WORLD =
        Component.text("Couldn't find which world to put you in! (Invalid world name)", NamedTextColor.RED)
    private val HANDSHAKE_FAILED =
        Component.text("Couldn't find which world to put you in! (Handshake failed)", NamedTextColor.RED)
    private val INVALID_PACKET_RECEIVED =
        Component.text("Invalid packet received during login process! (Proxy server error)", NamedTextColor.RED)

    private val players = mutableMapOf<String, PartialPlayer>()

    private class PartialPlayer {

        var player: Player? = null
            set(value) {
                field = value
                // Fetch the player's data document
                if (value != null) DatabaseModule.IO.launch {
                    DatabaseModule.loadDataDocument(value as CustomPlayer)
                    tryStartPlayState()
                }
            }

        var startingInstance: Instance? = null

        private var playing = false

        fun isReady() =
            !playing && player != null && startingInstance != null && player!!.playerConnection.connectionState == ConnectionState.LOGIN

        fun tryStartPlayState() {
            logger.trace("Trying to start play state")
            if (!isReady()) {
                logger.trace("Not ready, can't start play state; player = '$player', startingInstance = '$startingInstance'")
                return
            }
            playing = true
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                logger.debug("Starting PLAY state for Player '${player!!.username}'")
                MinecraftServer.getConnectionManager().startPlayState(player!!, true)
            }
            DatabaseModule.IO.launch {
                MessagingModule.Stubs.playerTrackerStub.playerTransfer(playerTransferRequest {
                    username = player!!.username
                    uuid = player!!.uuid.toString()
                    newServerName = MessagingModule.serverName
                    startingInstance?.uniqueId?.toString()?.let { newInstance = it }
                })
            }
        }
    }

    override fun hook(eventNode: EventNode<Event>) {
        MinecraftServer.getPacketListenerManager()
            .setListener(LoginStartPacket::class.java, ::handleLoginStart)
        MinecraftServer.getPacketListenerManager()
            .setListener(LoginPluginResponsePacket::class.java, ::handleLoginPluginMessage)
        MinecraftServer.getGlobalEventHandler()
            .addListener(PlayerLoginEvent::class.java, ::handlePlayerLogin)
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent::class.java) { event ->
            players.remove(event.player.username)
        }
    }

    private fun handleLoginStart(packet: LoginStartPacket, connection: PlayerConnection) {
        if (connection !is PlayerSocketConnection) return
        logger.debug("Player login start: ${packet.username}")
        // Send a request to Velocity to confirm the player's spawning instance
        val messageId = ThreadLocalRandom.current().nextInt()
        connection.addPluginRequestEntry(messageId, BLUEDRAGON_GET_DEST_CHANNEL)
        connection.sendPacket(LoginPluginRequestPacket(messageId, BLUEDRAGON_GET_DEST_CHANNEL, byteArrayOf())) // send to proxy
        // Use Minestom's handler to process the packet, which will send a request to Velocity for the player's UUID, username, skin, etc.
        LoginStartListener.listener(packet, connection)
    }

    private fun handleLoginPluginMessage(packet: LoginPluginResponsePacket, connection: PlayerConnection) {
        // `packet` is received from proxy
        if (connection !is PlayerSocketConnection) return
        val channel = connection.getPluginRequestChannel(packet.messageId)
        logger.debug("Login plugin response for ${connection.player?.username}: channel='$channel', packet=$packet")
        if (channel == BLUEDRAGON_GET_DEST_CHANNEL) {
            handleInstanceDestination(packet, connection)
        } else if (VelocityProxy.isEnabled() && channel == VELOCITY_PLAYER_INFO_CHANNEL) {
            handleVelocityForwarding(packet, connection)
        } else {
            logger.warn("Login plugin message received on unexpected channel: $channel")
        }
    }

    private fun handleInstanceDestination(packet: LoginPluginResponsePacket, connection: PlayerSocketConnection) {
        // Update the player's spawning instance, which was received from Velocity
        val reader = BinaryReader(packet.data)
        val instanceUid = reader.readUuid()
        val instance = MinecraftServer.getInstanceManager().getInstance(instanceUid)
        val username = connection.loginUsername!!
        logger.debug("Desired instance for player $username: $instance ($instanceUid)")
        if (instance == null) {
            logger.trace("Disconnecting player - desired instance ($instanceUid) is null")
            connection.sendPacket(LoginDisconnectPacket(INVALID_WORLD))
            return
        }
        logger.debug("Trying to send player $username to instance $instance")
        players.getOrPut(username) { PartialPlayer() }.apply {
            startingInstance = instance
            tryStartPlayState()
        }
    }

    private fun handleVelocityForwarding(packet: LoginPluginResponsePacket, connection: PlayerSocketConnection) {
        // Manually handle Velocity forwarding because we don't want to immediately go from the LOGIN to the PLAY state.
        // We want to wait for Velocity's plugin message as well as our instance routing message.
        if (packet.data == null) {
            connection.sendPacket(LoginDisconnectPacket(INVALID_PACKET_RECEIVED))
            return
        }
        val reader = BinaryReader(packet.data)
        val success = VelocityProxy.checkIntegrity(reader)

        if (!success) {
            logger.warn("Velocity proxy validation failed for connection: ${connection.identifier}")
            connection.sendPacket(LoginDisconnectPacket(INVALID_PROXY_RESPONSE))
            return
        }

        val addr = VelocityProxy.readAddress(reader)
        val port = (connection.remoteAddress as InetSocketAddress).port
        val providedUuid = reader.readUuid()
        val username = reader.readSizedString(16)
        val skin = VelocityProxy.readSkin(reader)
        connection.remoteAddress = InetSocketAddress(addr, port)
        connection.UNSAFE_setLoginUsername(username)
        val uuid = providedUuid ?: MinecraftServer.getConnectionManager().getPlayerConnectionUuid(connection, username)
        val player = MinecraftServer.getConnectionManager().playerProvider.createPlayer(uuid, username, connection)
        player.skin = skin
        logger.debug("Velocity modern forwarding succeeded for $username; created player object: $player")
        players.getOrPut(username) { PartialPlayer() }.apply {
            this.player = player
            tryStartPlayState()
        }
    }

    private fun handlePlayerLogin(event: PlayerLoginEvent) {
        // Check if the player's spawning instance was retrieved from Velocity
        if (!players.containsKey(event.player.username)) {
            // If there is no entry for the player, the handshake must have failed.
            event.player.sendPacket(LoginDisconnectPacket(HANDSHAKE_FAILED))
            event.player.playerConnection.disconnect()
            return
        }
        val instance = players[event.player.username]?.startingInstance
        players.remove(event.player.username)
        if (instance == null) {
            // If the instance was not set or doesn't exist, disconnect the player.
            logger.warn("No instance found for ${event.player.username} to join!")
            event.player.sendPacket(LoginDisconnectPacket(NO_WORLD_FOUND))
            event.player.playerConnection.disconnect()
            return
        }
        // If the instance exists, set the player's spawning instance and allow them to connect.
        logger.info("Spawning player ${event.player.username} in instance $instance")
        event.setSpawningInstance(instance)

        MinecraftServer.getSchedulerManager().scheduleNextTick {
            event.player.sendMessage(Component.translatable("global.instance.placing",
                NamedTextColor.GRAY,
                Component.text(instance.uniqueId.toString())))
            Game.findGame(instance.uniqueId)?.addPlayer(event.player)
        }
    }
}