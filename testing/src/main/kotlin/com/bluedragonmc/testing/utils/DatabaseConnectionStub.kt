package com.bluedragonmc.testing.utils

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.api.DatabaseConnection
import com.bluedragonmc.server.module.database.MapData
import com.bluedragonmc.server.module.database.PermissionGroup
import com.bluedragonmc.server.module.database.PlayerDocument
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.entity.Player
import java.util.*
import kotlin.reflect.KMutableProperty

class DatabaseConnectionStub : DatabaseConnection {
    override suspend fun getGroupByName(name: String): PermissionGroup? {
        return null
    }

    override suspend fun getPlayerDocument(username: String): PlayerDocument? {
        return null
    }

    override suspend fun getPlayerDocument(uuid: UUID): PlayerDocument? {
        return null
    }

    override suspend fun getPlayerDocument(player: Player): PlayerDocument {
        error("Method not mocked")
    }

    override suspend fun getNameColor(uuid: UUID): TextColor? {
        return null
    }

    override suspend fun getAllGroups(): List<PermissionGroup> {
        return emptyList()
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

    override suspend fun <T> updateGroup(groupName: String, field: KMutableProperty<T>, value: T) {

    }

    override suspend fun insertGroup(group: PermissionGroup) {

    }

    override suspend fun removeGroup(group: PermissionGroup) {

    }

    override fun loadDataDocument(player: CustomPlayer) {

    }
}