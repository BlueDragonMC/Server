@file:OptIn(ExperimentalSerializationApi::class)

package com.bluedragonmc.server.model

import com.bluedragonmc.server.service.Database
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1

@Serializable
data class PlayerDocument(
    @SerialName("_id") @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    var username: String = "",
    @EncodeDefault var usernameLower: String = username.lowercase(),
    var coins: Int = 0,
    var experience: Int = 0,
    var punishments: MutableList<Punishment> = mutableListOf(),
    var statistics: MutableMap<String, Double> = mutableMapOf(),
    var achievements: List<Achievement> = emptyList(),
    var cosmetics: List<CosmeticEntry> = emptyList(),
    val firstJoinDate: Long = System.currentTimeMillis(),
    var lastJoinDate: Long = System.currentTimeMillis(),
) {

    suspend fun <T> update(field: KMutableProperty<T>, value: T) {
        Database.connection.updatePlayer(uuid.toString(), field, value)
        field.setter.call(this, value)
    }

    suspend fun <T> compute(field: KMutableProperty1<PlayerDocument, T>, block: (T) -> T) {
        val newValue = block(field.get(this))
        Database.connection.updatePlayer(uuid.toString(), field, newValue)
        field.set(this, newValue)
    }
}

@Serializable
data class CosmeticEntry(val id: String, var equipped: Boolean = false)

enum class PunishmentType {
    BAN, MUTE
}

@Serializable
data class Punishment(
    val type: PunishmentType,
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = DateSerializer::class) val issuedAt: Date,
    @Serializable(with = DateSerializer::class) val expiresAt: Date,
    @Serializable(with = UUIDSerializer::class) val moderator: UUID,
    val reason: String,
    var active: Boolean = true,
) {
    fun isExpired() = expiresAt.before(Date.from(Instant.now()))
    fun isInEffect() = !isExpired() && active

    fun getTimeRemaining(): String {
        val expiration = Instant.ofEpochMilli(expiresAt.time)
        val now = Instant.now()
        val duration = Duration.between(now, expiration)
        return String.format(
            "%02dd %02dh %02dm %02ds",
            duration.toDaysPart(),
            duration.toHoursPart(),
            duration.toMinutesPart(),
            duration.toSecondsPart()
        )
    }
}

@Serializable
data class Achievement(
    val id: String,
    @Serializable(with = DateSerializer::class) val earnedAt: Date,
)

@Serializable
data class PlayerRecord(
    @SerialName("_id") @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    val username: String,
)

@Serializable
data class TeamRecord(
    @SerialName("_id") val name: String,
    val players: List<PlayerRecord>,
)

@Serializable
data class StatisticRecord(
    val key: String,
    val player: PlayerRecord,
    val oldValue: Double?,
    val newValue: Double,
)

@Serializable
data class InstanceRecord(
    @SerialName("_id") @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    val type: String,
)

@Serializable
data class GameDocument(
    val gameId: String,
    val serverId: String,
    val gameType: String,
    val mode: String?,
    val mapName: String,
    @EncodeDefault val teams: List<TeamRecord>? = listOf(),
    val winningTeam: TeamRecord?,
    @EncodeDefault val statistics: List<StatisticRecord>? = listOf(),
    @Serializable(with = DateSerializer::class) val startTime: Date?,
    @Serializable(with = DateSerializer::class) val endTime: Date,
    val instances: List<InstanceRecord>,
)