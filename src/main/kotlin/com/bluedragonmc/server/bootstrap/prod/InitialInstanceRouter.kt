package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.queue.ProductionEnvironment
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.extras.velocity.VelocityProxy
import net.minestom.server.instance.Instance
import net.minestom.server.network.ConnectionState
import net.minestom.server.network.packet.client.login.LoginPluginResponsePacket
import net.minestom.server.network.packet.client.login.LoginStartPacket
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket
import net.minestom.server.network.packet.server.login.LoginPluginRequestPacket
import net.minestom.server.network.player.PlayerConnection
import net.minestom.server.network.player.PlayerSocketConnection
import net.minestom.server.utils.binary.BinaryReader
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

object InitialInstanceRouter : Bootstrap(ProductionEnvironment::class) {

    private const val BLUEDRAGON_GET_DEST_CHANNEL = "bluedragonmc:get_dest"
    private const val VELOCITY_PLAYER_INFO_CHANNEL = VelocityProxy.PLAYER_INFO_CHANNEL

    private val INVALID_PROXY_RESPONSE = Component.text("Invalid proxy response!", NamedTextColor.RED)
    private val NO_WORLD_FOUND = Component.text("Couldn't find which world to put you in!", NamedTextColor.RED)

    private val cache: Cache<String, PartialPlayer> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(5))
        .build()

    private val instanceCache: Cache<Player, Instance> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(5))
        .build()

    private data class PartialPlayer(var player: Player? = null, var startingInstance: Instance? = null) {
        companion object {
            val EMPTY = PartialPlayer(null, null)
        }
    }

    override fun hook(eventNode: EventNode<Event>) {
        MinecraftServer.getPacketListenerManager()
            .setListener(LoginStartPacket::class.java, ::handleLoginStart)
        MinecraftServer.getPacketListenerManager()
            .setListener(LoginPluginResponsePacket::class.java, ::handleLoginPluginMessage)
        MinecraftServer.getGlobalEventHandler()
            .addListener(PlayerLoginEvent::class.java, ::handlePlayerLogin)
    }

    private fun handleLoginStart(packet: LoginStartPacket, connection: PlayerConnection) {
        logger.trace("Player login start: ${packet.username} (${packet.profileId})")
        // Send a request to Velocity to confirm the player's spawning instance
        val connection = connection as? PlayerSocketConnection ?: return
        val messageId = ThreadLocalRandom.current().nextInt()
        val bytes = packet.profileId.toString().toByteArray(StandardCharsets.UTF_8)
        connection.addPluginRequestEntry(messageId, BLUEDRAGON_GET_DEST_CHANNEL)
        connection.sendPacket(LoginPluginRequestPacket(messageId,
            BLUEDRAGON_GET_DEST_CHANNEL,
            bytes)) // send to proxy
        // Use Minestom's handler to process the packet, which will send a request to Velocity for the player's UUID, username, skin, etc.
        packet.process(connection)
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
        logger.info("Desired instance for player $username: $instance ($instanceUid)")
        if (instance == null) {
            logger.info("Disconnecting player - desired instance ($instanceUid) is null")
            connection.sendPacket(LoginDisconnectPacket(NO_WORLD_FOUND))
        }
        logger.debug("Trying to send player $username to instance $instance")
        synchronized(cache) {
            cache.put(username,
                (cache.getIfPresent(username) ?: PartialPlayer.EMPTY).apply {
                    this.startingInstance = instance
                })
            tryStartPlayState(username)
        }
    }

    private fun handleVelocityForwarding(packet: LoginPluginResponsePacket, connection: PlayerSocketConnection) {
        // Manually handle Velocity forwarding because we don't want to immediately go from the LOGIN to the PLAY state.
        // We want to wait for Velocity's plugin message as well as our instance routing message.
        val reader = BinaryReader(packet.data)
        val success = VelocityProxy.checkIntegrity(reader)

        if (success) {
            val addr = VelocityProxy.readAddress(reader)
            val port = (connection.remoteAddress as InetSocketAddress).port
            val providedUuid = reader.readUuid()
            val username = reader.readSizedString(16)
            val skin = VelocityProxy.readSkin(reader)
            connection.remoteAddress = InetSocketAddress(addr, port)
            connection.UNSAFE_setLoginUsername(username)
            val uuid = providedUuid ?: MinecraftServer.getConnectionManager()
                .getPlayerConnectionUuid(connection, username)
            val player = MinecraftServer.getConnectionManager().playerProvider.createPlayer(uuid,
                username,
                connection)
            player.skin = skin
            logger.debug("Velocity modern forwarding succeeded for $username; created player object: $player")
            synchronized(cache) {
                cache.put(username,
                    (cache.getIfPresent(username) ?: PartialPlayer.EMPTY).apply { this.player = player })
                tryStartPlayState(username)
            }
        } else {
            logger.warn("Velocity proxy validation failed")
            connection.sendPacket(LoginDisconnectPacket(INVALID_PROXY_RESPONSE))
        }
    }

    private fun handlePlayerLogin(event: PlayerLoginEvent) {
        // Check if the player's spawning instance was retrieved from Velocity
        val instance = instanceCache.getIfPresent(event.player)
        logger.info("Player ${event.player.username} is logging in to instance $instance...")
        instanceCache.invalidate(event.player)
        if (instance == null) {
            // If the instance was not set or doesn't exist, disconnect the player.
            logger.info("No instance found for ${event.player.username} to join!")
            event.player.kick(NO_WORLD_FOUND)
            return
        }
        // If the instance exists, set the player's spawning instance and allow them to connect.
        logger.info("Spawning player ${event.player.username} in instance $instance")
        event.setSpawningInstance(instance)
    }

    private fun tryStartPlayState(id: String) {
        logger.debug("Trying to start play state for '$id'")
        val entry = cache.getIfPresent(id) ?: return
        if (entry.startingInstance != null && entry.player != null && entry.player!!.playerConnection.connectionState == ConnectionState.LOGIN) {
            logger.debug("Starting PLAY state for Player '$id'")
            cache.invalidate(id)
            instanceCache.put(entry.player!!, entry.startingInstance!!)
            logger.debug("connection before setting PLAY state: ${entry.player?.playerConnection}")
            MinecraftServer.getConnectionManager().startPlayState(entry.player!!, true)
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                logger.debug("connection after setting PLAY state (next tick): ${entry.player?.playerConnection}")
            }
        } else {
            logger.debug("Missing required info before starting play state: instance='${entry.startingInstance}', player='${entry.player}'")
        }
    }
}