package com.bluedragonmc.server.api

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.module.database.MapData
import com.bluedragonmc.server.module.database.PermissionGroup
import com.bluedragonmc.server.module.database.PlayerDocument
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.entity.Player
import java.util.*
import kotlin.reflect.KMutableProperty

interface DatabaseConnection {

    fun loadDataDocument(player: CustomPlayer)

    suspend fun getGroupByName(name: String): PermissionGroup?

    suspend fun getPlayerDocument(username: String): PlayerDocument?

    suspend fun getPlayerDocument(uuid: UUID): PlayerDocument?

    suspend fun getPlayerDocument(player: Player): PlayerDocument

    suspend fun getNameColor(uuid: UUID): TextColor?

    suspend fun getAllGroups(): List<PermissionGroup>

    suspend fun getMapOrNull(mapName: String): MapData?

    suspend fun rankPlayersByStatistic(key: String, sortCriteria: String, limit: Int): List<PlayerDocument>

    suspend fun getPlayerForPunishmentId(id: String): PlayerDocument?

    suspend fun <T> updatePlayer(playerUuid: String, field: KMutableProperty<T>, value: T)

    suspend fun <T> updateGroup(groupName: String, field: KMutableProperty<T>, value: T)

    suspend fun insertGroup(group: PermissionGroup)

    suspend fun removeGroup(group: PermissionGroup)
}