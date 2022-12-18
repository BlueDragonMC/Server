@file:OptIn(ExperimentalSerializationApi::class)

package com.bluedragonmc.server.model

import com.bluedragonmc.server.model.*
import com.bluedragonmc.server.service.Database
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.coordinate.Pos
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1

@Serializable
data class PlayerDocument constructor(
    @SerialName("_id") @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    var username: String = "",
    @EncodeDefault var usernameLower: String = username.lowercase(),
    var coins: Int = 0,
    var experience: Int = 0,
    var punishments: MutableList<Punishment> = mutableListOf(),
    var statistics: MutableMap<String, Double> = mutableMapOf(),
    var achievements: List<Achievement> = emptyList(),
    var cosmetics: List<CosmeticEntry> = emptyList(),
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
        return String.format("%02dd %02dh %02dm %02ds",
            duration.toDaysPart(),
            duration.toHoursPart(),
            duration.toMinutesPart(),
            duration.toSecondsPart())
    }
}

@Serializable
data class Achievement(
    val id: String,
    @Serializable(with = DateSerializer::class) val earnedAt: Date,
)

@Serializable
data class MapData(
    @SerialName("_id") val name: String,
    val author: String = "BlueDragon Build Team",
    val description: String = "An awesome map!",
    val time: Int? = null,
    val spawnpoints: List<@Serializable(with = PosSerializer::class) Pos> = emptyList(),
    /**
     * A list of lists of positions. Use this to store game-specific locations like loot generators or NPCs.
     */
    val additionalLocations: List<List<@Serializable(with = PosSerializer::class) Pos>> = emptyList(),
)