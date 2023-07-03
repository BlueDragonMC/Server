package com.bluedragonmc.server

import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.bootstrap.*
import com.bluedragonmc.server.bootstrap.dev.DevInstanceRouter
import com.bluedragonmc.server.bootstrap.dev.MojangAuthentication
import com.bluedragonmc.server.bootstrap.dev.OpenToLAN
import com.bluedragonmc.server.bootstrap.prod.AgonesIntegration
import com.bluedragonmc.server.bootstrap.prod.InitialInstanceRouter
import com.bluedragonmc.server.bootstrap.prod.VelocityForwarding
import com.bluedragonmc.server.queue.GameLoader
import com.bluedragonmc.server.queue.createEnvironment
import net.minestom.server.MinecraftServer
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import org.slf4j.LoggerFactory
import java.text.DateFormat
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

lateinit var lobby: Game
fun isLobbyInitialized() = ::lobby.isInitialized
private val logger = LoggerFactory.getLogger("ServerKt")

fun main() {
    val commitDate = GitVersionInfo.commitDate
    if (commitDate != null) {
        val str = DateFormat.getDateInstance().format(commitDate)
        logger.info("Starting server version ${GitVersionInfo.BRANCH}/${GitVersionInfo.COMMIT} ($str)")
    } else {
        logger.info("Starting server version ${GitVersionInfo.BRANCH}/${GitVersionInfo.COMMIT}")
    }

    val time = measureTimeMillis(::start)
    logger.info("Game server started in ${time}ms.")
}

fun start() {

    Environment.setEnvironment(createEnvironment())
    logger.info("Starting Minecraft server in environment ${Environment.current::class.simpleName}")

    val minecraftServer = MinecraftServer.init()
    val eventNode = MinecraftServer.getGlobalEventHandler()

    val services = listOf(
        AgonesIntegration,
        Commands,
        CustomPlayerProvider,
        DevInstanceRouter,
        ExceptionHandler,
        GlobalBlockHandlers,
        GlobalChatFormat,
        GlobalPlayerNameFormat,
        GlobalPunishments,
        GlobalTranslation,
        InitialInstanceRouter,
        IntegrationsInit,
        MojangAuthentication,
        OpenToLAN,
        PerInstanceChat,
        PerInstanceTabList,
        ServerListPingHandler,
        TabListFormat,
        VelocityForwarding
    ).filter { it.canHook() }

    // Load game plugins and preinitialize their main classes
    GameLoader.loadGames()

    services.forEach {
        logger.debug("Initializing service ${it::class.simpleName ?: it::class.qualifiedName}")
        it.hook(eventNode)
    }

    logger.info("Initialized ${services.size} services in environment ${Environment.current::class.simpleName}.")

    // Register custom dimension types
    MinecraftServer.getDimensionTypeManager()
        .addDimension(DimensionType.builder(NamespaceID.from("$NAMESPACE:fullbright_dimension")).ambientLight(1.0F)
            .build())

    // Start the queue, allowing players to queue for and join games
    Environment.queue.start()

    // Start the server & bind to port 25565
    minecraftServer.start("0.0.0.0", 25565)

    // Create a Lobby instance
    lobby = try {
        GameLoader.createNewGame("Lobby", null, null)
    } catch (e: Throwable) {
        logger.error("There was an error initializing the Lobby. Shutting down...")
        e.printStackTrace()
        MinecraftServer.stopCleanly()
        exitProcess(1)
    }
}
