package com.bluedragonmc.server

import com.bluedragonmc.server.command.*
import com.bluedragonmc.server.game.Lobby
import com.bluedragonmc.server.module.gameplay.SpawnpointModule
import com.bluedragonmc.server.queue.IPCQueue
import com.bluedragonmc.server.queue.Queue
import com.bluedragonmc.server.queue.TestQueue
import com.bluedragonmc.server.utils.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.event.server.ServerListPingEvent
import net.minestom.server.extras.MojangAuth
import net.minestom.server.extras.lan.OpenToLAN
import org.apache.commons.net.util.Base64
import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetAddress
import java.nio.charset.Charset
import kotlin.reflect.jvm.internal.impl.descriptors.Named

/**
 * If in a develoment environment, the test queue is used.
 * If inside a Docker container, the IPCQueue is used.
 */
val queue: Queue = if (File("/server").exists()) IPCQueue else TestQueue()
val messagingDisabled = queue is TestQueue
val mongoHostname = if (messagingDisabled) "localhost" else "mongo"
lateinit var lobby: Game

private val logger = LoggerFactory.getLogger("ServerKt")

fun main() {
    logger.info("Using queue type: ${queue::class.simpleName}")
    val minecraftServer = MinecraftServer.init()

    // Create a test instance
    lobby = Lobby()

    // Make players spawn in the test instance
    MinecraftServer.getGlobalEventHandler().addListener(PlayerLoginEvent::class.java) { event ->
        event.player.respawnPoint = lobby.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(event.player)
        event.setSpawningInstance(lobby.getInstance())
    }

    // Chat formatting
    MinecraftServer.getGlobalEventHandler().addListener(PlayerChatEvent::class.java) { event ->
        event.setChatFormat {
            Component.join(
                JoinConfiguration.noSeparators(),
                event.player.name,
                Component.text(": ", NamedTextColor.DARK_GRAY),
                Component.text(event.message, NamedTextColor.WHITE)
            )
        }
    }

    MinecraftServer.getGlobalEventHandler().addListener(ServerListPingEvent::class.java) { event ->
        event.responseData.description = buildComponent {
            +("Blue" withColor BRAND_COLOR_PRIMARY_2 withDecoration TextDecoration.BOLD)
            +("Dragon" withColor BRAND_COLOR_PRIMARY_1 withDecoration TextDecoration.BOLD)
            +(" [" withColor NamedTextColor.DARK_GRAY)
            if (messagingDisabled) {
                +("Dev on ${InetAddress.getLocalHost().hostName}" withColor NamedTextColor.RED)
            } else {
                +(event.responseData.version withColor NamedTextColor.GREEN)
            }
            +("]\n" withColor NamedTextColor.DARK_GRAY)
            +SERVER_NEWS
        }
        event.responseData.favicon = "data:image/png;base64," + String(Base64.encodeBase64(File("favicon_64.png").readBytes()), Charset.forName("UTF-8"))
    }

    // Initialize commands
    listOf(
        JoinCommand("join", "/join <game>"),
        InstanceCommand("instance", "/instance <list|add|remove> ...", "in"),
        GameCommand("game", "/game <start|end>"),
        LobbyCommand("lobby", "/lobby", "l", "hub"),
        TeleportCommand("tp", "/tp <player> | /tp <x> <y> <z>"),
        FlyCommand("fly"),
        GameModeCommand("gamemode", "/gamemode <survival|creative|adventure|spectator> [player]", "gm"),
        KillCommand("kill", "/kill [player]"),
        SetBlockCommand("setblock", "/setblock <x> <y> <z> <block>"),
        PartyCommand("party", "/party <invite|kick|promote|warp|chat|list> ...", "p"),
        GiveCommand("give", "/give [player] <item>")
    ).forEach(MinecraftServer.getCommandManager()::register)

    // Set a custom player provider, so we can easily add fields to the Player class
    MinecraftServer.getConnectionManager().setPlayerProvider(::CustomPlayer)

    // Start the queue loop, which runs every 2 seconds and handles the players in queue
    queue.start()

    // Enable Mojang authentication (if we add a proxy, disable this)
    MojangAuth.init()

    // Start the server & bind to port 25565
    minecraftServer.start("0.0.0.0", 25565)

    OpenToLAN.open()

}
