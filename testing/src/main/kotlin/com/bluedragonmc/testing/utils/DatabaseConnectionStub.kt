package com.bluedragonmc.testing.utils

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.api.DatabaseConnection
import com.bluedragonmc.server.model.EventLog
import com.bluedragonmc.server.model.MapData
import com.bluedragonmc.server.model.PlayerDocument
import net.minestom.server.entity.Player
import java.util.*
import kotlin.reflect.KMutableProperty

class DatabaseConnectionStub : DatabaseConnection {

    override suspend fun getPlayerDocument(username: String): PlayerDocument? {
        return null
    }

    override suspend fun getPlayerDocument(uuid: UUID): PlayerDocument? {
        return null
    }

    override suspend fun getPlayerDocument(player: Player): PlayerDocument {
        error("Method not mocked")
    }

    override suspend fun getMapOrNull(mapName: String): MapData? {
        return null
    }

    override suspend fun rankPlayersByStatistic(
        key: String,
        sortCriteria: String,
        limit: Int,
    ): List<PlayerDocument> {
        return emptyList()
    }

    override suspend fun getPlayerForPunishmentId(id: String): PlayerDocument? {
        return null
    }

    override suspend fun <T> updatePlayer(playerUuid: String, field: KMutableProperty<T>, value: T) {

    }

    override suspend fun logEvent(event: EventLog) {

    }

    override fun loadDataDocument(player: CustomPlayer) {

    }
}