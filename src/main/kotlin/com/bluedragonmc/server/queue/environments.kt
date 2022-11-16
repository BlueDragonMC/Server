package com.bluedragonmc.server.queue

import com.bluedragonmc.games.arenapvp.ArenaPvpGame
import com.bluedragonmc.games.bedwars.BedWarsGame
import com.bluedragonmc.games.fastfall.FastFallGame
import com.bluedragonmc.games.infection.InfectionGame
import com.bluedragonmc.games.infinijump.InfinijumpGame
import com.bluedragonmc.games.pvpmaster.PvpMasterGame
import com.bluedragonmc.games.skyfall.SkyfallGame
import com.bluedragonmc.games.skywars.SkyWarsGame
import com.bluedragonmc.games.wackymaze.WackyMazeGame
import com.bluedragonmc.server.GitVersionInfo
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.bootstrap.prod.AgonesIntegration
import java.io.File
import java.util.*

val games = hashMapOf(
    "WackyMaze" to ::WackyMazeGame,
//  "TeamDeathmatch" to ::TeamDeathmatchGame,
    "BedWars" to ::BedWarsGame,
    "SkyWars" to ::SkyWarsGame,
    "FastFall" to ::FastFallGame,
    "Infection" to ::InfectionGame,
    "Infinijump" to ::InfinijumpGame,
    "PvPMaster" to ::PvpMasterGame,
    "ArenaPvP" to ::ArenaPvpGame,
    "Skyfall" to ::SkyfallGame,
)

fun createEnvironment() = if (isDev()) DevelopmentEnvironment() else ProductionEnvironment()
private fun isDev() = !File("/server").exists()

class DevelopmentEnvironment : Environment() {
    override val queue: Queue = TestQueue()
    override val mongoHostname: String = "localhost"
    override val puffinHostname: String = "localhost"
    override val gameClasses = games.keys
    override val versionInfo = GitVersionInfo

    private lateinit var serverName: String

    override suspend fun getServerName(): String {
        if (!::serverName.isInitialized) {
            serverName = "Dev-" + UUID.randomUUID().toString().take(5)
        }
        return serverName
    }
}

class ProductionEnvironment : Environment() {
    override val queue: Queue = IPCQueue
    override val mongoHostname: String = "mongo"
    override val puffinHostname: String = "puffin"
    override val gameClasses = games.keys
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
    override val gameClasses = games.keys
    override val versionInfo = GitVersionInfo
    override val dbName: String = "TESTENV"

    override suspend fun getServerName() = UUID(0L, 0L).toString()

}