package com.bluedragonmc.server

import com.bluedragonmc.server.bootstrap.*
import com.bluedragonmc.server.game.Lobby
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import org.slf4j.LoggerFactory

const val NAMESPACE = "bluedragon"
lateinit var lobby: Game
private val logger = LoggerFactory.getLogger("ServerKt")

fun main() {

    logger.info("Starting Minecraft server in environment ${Environment.current::class.simpleName}")
    val minecraftServer = MinecraftServer.init()
    val eventNode = MinecraftServer.getGlobalEventHandler()

    // Create a test instance
    lobby = Lobby()

    val services = listOf(
        Commands,
        OpenToLAN,
        GlobalPunishments,
        GlobalChatFormat,
        PerInstanceTabList,
        CustomPlayerProvider,
        ServerListPingHandler,
        InitialInstanceRouter,
        DevInstanceRouter,
        CustomExceptionHandler,
        ScheduledServerShutdown,
        VelocityForwarding,
        MojangAuthentication,
        GlobalPlayerNameFormat,
        GlobalTranslation,
        GlobalBlockHandlers
    ).filter { it.canHook() }

    services.forEach {
        logger.debug("Initializing service ${it::class.simpleName ?: it::class.qualifiedName}")
        it.hook(eventNode)
    }

    logger.info("Initialized ${services.size} services in environment ${Environment.current::class.simpleName}.")

    eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
        event.player.sendPlayerListHeaderAndFooter(
            SERVER_NAME_GRADIENT.decorate(TextDecoration.BOLD),
            Component.translatable("global.tab.call_to_action", BRAND_COLOR_PRIMARY_2, Component.translatable("global.server.domain", BRAND_COLOR_PRIMARY_1)))
    }

    // Register custom dimension types
    MinecraftServer.getDimensionTypeManager().addDimension(
        DimensionType.builder(NamespaceID.from("$NAMESPACE:fullbright_dimension")).ambientLight(1.0F).build()
    )

    // Start the queue, allowing players to queue for and join games
    Environment.current.queue.start()

    // Start the server & bind to port 25565
    minecraftServer.start("0.0.0.0", 25565)

    if (AgonesIntegration.canHook()) AgonesIntegration.hook(eventNode)
}
