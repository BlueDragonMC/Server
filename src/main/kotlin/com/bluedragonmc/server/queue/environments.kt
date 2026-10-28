package com.bluedragonmc.server.queue

import com.bluedragonmc.server.GitVersionInfo
import com.bluedragonmc.server.VersionInfo
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.Queue
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

fun createEnvironment() = ConfiguredEnvironment()
private fun isDev(): Boolean {
    if (System.getenv("BLUEDRAGON_ENV_TYPE") != null) {
        return System.getenv("BLUEDRAGON_ENV_TYPE").uppercase() in listOf("DEV", "DEVELOPMENT", "TEST", "TESTING")
    }
    // Fallback method
    return !File("/server").exists()
}

class ConfiguredEnvironment : Environment() {

    override val queue: Queue = when (System.getenv("BLUEDRAGON_QUEUE_TYPE")?.uppercase()) {
        "IPCQUEUE", "IPC", "PROD" -> IPCQueue
        "TESTQUEUE", "TEST", "DEV" -> TestQueue()
        else -> defaultQueue()
    }

    override val mongoConnectionString: String =
        System.getenv("BLUEDRAGON_MONGO_CONNECTION_STRING") ?: defaultMongoConnectionString()
    override val puffinHostname: String = System.getenv("BLUEDRAGON_PUFFIN_HOSTNAME") ?: defaultPuffinHostname()
    override val puffinPort: Int = System.getenv("BLUEDRAGON_PUFFIN_PORT")?.toIntOrNull() ?: 50051
    override val grpcServerPort: Int = System.getenv("BLUEDRAGON_GRPC_SERVER_PORT")?.toIntOrNull() ?: 50051
    override val luckPermsHostname: String =
        System.getenv("BLUEDRAGON_LUCKPERMS_HOSTNAME") ?: defaultLuckPermsHostname()

    override val gameClasses: Collection<String> = GameLoader.gameNames
    override val versionInfo: VersionInfo = GitVersionInfo
    override val isDev: Boolean = isDev()

    override val defaultGameName: String = System.getenv("BLUEDRAGON_DEFAULT_GAME") ?: super.defaultGameName

    private lateinit var serverName: String

    override suspend fun getServerName(): String {
        if (!::serverName.isInitialized) {
            serverName = System.getenv("BLUEDRAGON_SERVER_NAME")?.takeIf { it.isNotBlank() }
                ?: System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
                        ?: ("dev-" + UUID.randomUUID().toString().take(5) + "-" + UUID.randomUUID().toString().take(5))
        }
        return serverName
    }
}

private fun defaultQueue(): Queue {
    LoggerFactory.getLogger(Environment::class.java)
        .warn("No environment variable specified, using default Queue configuration.")
    return if (isDev()) TestQueue() else IPCQueue
}

private fun defaultMongoConnectionString(): String {
    LoggerFactory.getLogger(Environment::class.java)
        .warn("No environment variable specified, using default MongoDB connection configuration.")
    return if (isDev()) "mongodb://localhost:27017" else "mongodb://mongo:27017"
}

private fun defaultPuffinHostname(): String {
    LoggerFactory.getLogger(Environment::class.java)
        .warn("No environment variable specified, using default Puffin connection configuration.")
    return if (isDev()) "localhost" else "puffin"
}

private fun defaultLuckPermsHostname(): String {
    LoggerFactory.getLogger(Environment::class.java)
        .warn("No environment variable specified, using default LuckPerms connection configuration.")
    return if (isDev()) "http://localhost:8080" else "http://luckperms:8080"
}