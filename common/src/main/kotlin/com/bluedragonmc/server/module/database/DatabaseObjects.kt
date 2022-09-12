package com.bluedragonmc.server.module.database

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.item.Material
import org.litote.kmongo.setValue
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1

@Serializable
data class PlayerDocument @OptIn(ExperimentalSerializationApi::class) constructor(
    @SerialName("_id") @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    var username: String = "",
    @EncodeDefault var usernameLower: String = username.lowercase(),
    var coins: Int = 0,
    var experience: Int = 0,
    var groups: MutableList<String> = mutableListOf("default"),
    var punishments: MutableList<Punishment> = mutableListOf(),
    var statistics: MutableMap<String, Double> = mutableMapOf(),
    var achievements: List<Achievement> = emptyList(),
    var ownedCosmetics: List<Cosmetic> = emptyList(),
    var permissions: MutableList<String> = mutableListOf(),
) {

    val highestGroup by lazy {
        runBlocking {
            getGroups().maxByOrNull { it.priority }
        }
    }

    suspend fun getGroups(): List<PermissionGroup> {
        return groups.mapNotNull { name ->
            DatabaseModule.getGroupByName(name)
        }.toList()
    }

    suspend fun getAllPermissions(): List<String> =
        permissions + getGroups().map { it.permissions }.flatten().distinct()

    suspend fun <T> update(field: KMutableProperty<T>, value: T) {
        DatabaseModule.getPlayersCollection().updateOneById(uuid.toString(), setValue(field, value))
        field.setter.call(this, value)
    }

    suspend fun <T> compute(field: KMutableProperty1<PlayerDocument, T>, block: (T) -> T) {
        val newValue = block(field.get(this))
        DatabaseModule.getPlayersCollection().updateOneById(uuid.toString(), setValue(field, newValue))
        field.set(this, newValue)
    }

    init {
        DatabaseModule.IO.launch {
            highestGroup // Initialize the highest group async when the document is created
        }
    }
}

enum class Cosmetic(val displayName: String, val description: String, val icon: Material, val unlockCost: Int)

enum class PunishmentType {
    BAN, MUTE, WARNING, COMPETITIVE_BAN
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

enum class AchievementType(
    val displayName: String,
    val description: String,
) {
    JOIN_SERVER("Welcome to BlueDragon!", "Join the server for the first time"),
}

@Serializable
data class Achievement(
    val key: AchievementType,
    @Serializable(with = DateSerializer::class) val earnedAt: Date,
)

@Serializable
data class PermissionGroup(
    @SerialName("_id") val name: String,
    @Serializable(with = TextColorSerializer::class) val color: TextColor = NamedTextColor.WHITE,
    @Serializable(with = ComponentSerializer::class) val prefix: Component = Component.empty(),
    val priority: Int = 0,
    var permissions: MutableList<String> = mutableListOf(),
    val inheritsFrom: List<String> = emptyList(),
) {
    suspend fun getChildGroups(): List<PermissionGroup?> {
        return inheritsFrom.map { name ->
            DatabaseModule.getGroupByName(name)
        }
    }

    suspend fun getAllPermissions(): List<String> {
        return permissions + getChildGroups().map { getAllPermissions() }.flatten()
    }

    suspend fun <T> update(field: KMutableProperty<T>, value: T) {
        DatabaseModule.getGroupsCollection().updateOneById(name, setValue(field, value))
        field.setter.call(this, value)
    }
}

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