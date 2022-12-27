package com.bluedragonmc.server.queue

import com.bluedragonmc.server.GitVersionInfo
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.bootstrap.prod.AgonesIntegration
import java.io.File
import java.util.*

fun createEnvironment() = if (isDev()) DevelopmentEnvironment() else ProductionEnvironment()
private fun isDev() = !File("/server").exists()

class DevelopmentEnvironment : Environment() {
    override val queue: Queue = TestQueue()
    override val mongoHostname: String = "localhost"
    override val puffinHostname: String = "localhost"
    override val gameClasses = GameLoader.gameNames
    override val versionInfo = GitVersionInfo

    private lateinit var serverName: String

    override suspend fun getServerName(): String {
        if (!::serverName.isInitialized) {
            serverName = "dev-" + UUID.randomUUID().toString().take(5) + "-" + UUID.randomUUID().toString().take(5)
        }
        return serverName
    }
}

class ProductionEnvironment : Environment() {
    override val queue: Queue = IPCQueue
    override val mongoHostname: String = "mongo"
    override val puffinHostname: String = "puffin"
    override val gameClasses = GameLoader.gameNames
    override val versionInfo = GitVersionInfo

    private lateinit var serverName: String

    override suspend fun getServerName(): String {
        if (!::serverName.isInitialized) {
            serverName = System.getenv("PUFFIN_CONTAINER_ID")
                ?: AgonesIntegration.stub.getGameServer(AgonesIntegration.empty).objectMeta.name
        }
        return serverName
    }
}

class LocalTestingEnvironment : Environment() {
    override val queue: Queue
        get() = error("Testing environment has no default Queue.")
    override val mongoHostname: String = "localhost"
    override val puffinHostname: String = "localhost"
    override val gameClasses = GameLoader.gameNames
    override val versionInfo = GitVersionInfo
    override val dbName: String = "TESTENV"

    override suspend fun getServerName() = UUID(0L, 0L).toString()

}