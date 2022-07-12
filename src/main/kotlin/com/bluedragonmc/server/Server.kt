package com.bluedragonmc.server

import com.bluedragonmc.server.command.*
import com.bluedragonmc.server.game.Lobby
import com.bluedragonmc.server.module.gameplay.SpawnpointModule
import com.bluedragonmc.server.queue.IPCQueue
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.extras.lan.OpenToLAN

val queue = IPCQueue
lateinit var lobby: Game

/**
 * Light color, often used for emphasis.
 */
val BRAND_COLOR_PRIMARY_1 = TextColor.color(0x4EB2F4)

/**
 * Medium color, often used for chat messages.
 */
val BRAND_COLOR_PRIMARY_2 = TextColor.color(0x2792f7) // Medium, often used for chat messages

/**
 * Very dark color.
 */
val BRAND_COLOR_PRIMARY_3 = TextColor.color(0x3336f4) // Very dark
val ALT_COLOR_1 = NamedTextColor.YELLOW
const val SERVER_IP = "bluedragonmc.com"
fun main() {
    println("Woohoo!")
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
        event.setChatFormat { Component.join(JoinConfiguration.noSeparators(),
            event.player.name,
            Component.text(": ", NamedTextColor.DARK_GRAY),
            Component.text(event.message, NamedTextColor.WHITE))}
    }

    // Initialize commands
    listOf(
        JoinCommand("join", "/join <game>"),
        InstanceCommand("instance", "/instance <list|add|remove> ...", "in"),
        GameCommand("game", "/game <start|end>"),
        LobbyCommand("lobby", "/lobby", "l", "hub"),
        TeleportCommand("tp", "/tp <player> | /tp <x> <y> <z>"),
        FlyCommand("fly"),
        GameModeCommand("gamemode", "/gamemode <survival|creative|adventure|spectator> [player]", "gm")
    ).forEach(MinecraftServer.getCommandManager()::register)

    // Set a custom player provider, so we can easily add fields to the Player class
    MinecraftServer.getConnectionManager().setPlayerProvider(::CustomPlayer)

    // Start the queue loop, which runs every 2 seconds and handles the players in queue
    queue.start()

    // Start the server & bind to port 25565
    minecraftServer.start("0.0.0.0", 25565)

    OpenToLAN.open()

}
