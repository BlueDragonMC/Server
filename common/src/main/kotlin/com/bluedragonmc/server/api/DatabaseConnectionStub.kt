package com.bluedragonmc.server.api

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.model.GameDocument
import com.bluedragonmc.server.model.PlayerDocument
import net.minestom.server.entity.Player
import java.util.UUID
import kotlin.reflect.KMutableProperty

class DatabaseConnectionStub : DatabaseConnection {

    override suspend fun loadDataDocument(player: CustomPlayer) {}

    override suspend fun getPlayerDocument(username: String): PlayerDocument? = null

    override suspend fun getPlayerDocument(uuid: UUID): PlayerDocument? = null

    override suspend fun getPlayerDocument(player: Player): PlayerDocument =
        PlayerDocument(player.uuid, player.username)

    override suspend fun rankPlayersByStatistic(key: String, sortCriteria: String, limit: Int): List<PlayerDocument> =
        emptyList()

    override suspend fun getPlayerForPunishmentId(id: String): PlayerDocument? = null

    override suspend fun <T> updatePlayer(playerUuid: String, field: KMutableProperty<T>, value: T) {}

    override suspend fun logGame(game: GameDocument) {}
}
