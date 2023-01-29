package com.bluedragonmc.server.api

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.model.EventLog
import com.bluedragonmc.server.model.MapData
import com.bluedragonmc.server.model.PlayerDocument
import net.minestom.server.entity.Player
import java.util.*
import kotlin.reflect.KMutableProperty

interface DatabaseConnection {

    fun loadDataDocument(player: CustomPlayer)

    suspend fun getPlayerDocument(username: String): PlayerDocument?

    suspend fun getPlayerDocument(uuid: UUID): PlayerDocument?

    suspend fun getPlayerDocument(player: Player): PlayerDocument

    suspend fun getMapOrNull(mapName: String): MapData?

    suspend fun rankPlayersByStatistic(key: String, sortCriteria: String, limit: Int): List<PlayerDocument>

    suspend fun getPlayerForPunishmentId(id: String): PlayerDocument?

    suspend fun <T> updatePlayer(playerUuid: String, field: KMutableProperty<T>, value: T)

    suspend fun logEvent(event: EventLog)
}
