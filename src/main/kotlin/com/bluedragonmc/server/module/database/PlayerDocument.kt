package com.bluedragonmc.server.module.database

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.item.Material
import net.minestom.server.permission.Permission
import org.litote.kmongo.setTo
import org.litote.kmongo.setValue
import java.util.*
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty1

@Serializable
data class PlayerDocument(
    @SerialName("_id") @Serializable(with = DatabaseModule.UUIDSerializer::class) val uuid: UUID,
    var coins: Int = 0,
    val groups: List<String> = emptyList(),
    val punishments: List<Punishment> = emptyList(),
    val statistics: List<Statistic> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
    val ownedCosmetics: List<Cosmetic> = emptyList(),
) {
    suspend fun getGroups(): List<PermissionGroup> {
        val col = DatabaseModule.getGroupsCollection()
        return groups.mapNotNull {
            col.findOneById(it)
        }.toList()
    }

    suspend fun getAllPermissions(): List<Permission> =
        getGroups().map { it.permissions }.flatten().distinctBy { it.permissionName }

    suspend fun <T> update(field: KMutableProperty<T>, value: T) {
        DatabaseModule.getPlayersCollection().updateOneById(uuid.toString(), setValue(field, value))
        field.setTo(value)
    }

    suspend fun <T> compute(field: KMutableProperty1<PlayerDocument, T>, block: (T) -> T) {
        val newValue = block(field.get(this))
        DatabaseModule.getPlayersCollection().updateOneById(uuid.toString(), setValue(field, newValue))
        field.set(this, newValue)
    }
}

enum class Cosmetic(val displayName: String, val description: String, val icon: Material, val unlockCost: Int)

@Serializable
data class Statistic(val key: String, val value: Double)

enum class PunishmentType {
    BAN, MUTE, WARNING, COMPETITIVE_BAN
}

@Serializable
data class Punishment(
    val type: PunishmentType,
    @Serializable(with = DatabaseModule.UUIDSerializer::class) val id: UUID,
    @Serializable(with = DatabaseModule.DateSerializer::class) val issuedAt: Date,
    @Serializable(with = DatabaseModule.DateSerializer::class) val expiresAt: Date,
    @Serializable(with = DatabaseModule.UUIDSerializer::class) val moderator: UUID,
    val reason: String,
)

enum class AchievementType(
    val displayName: String,
    val description: String,
) {
    JOIN_SERVER("Welcome to BlueDragon!", "Join the server for the first time"),
}

@Serializable
data class Achievement(
    val key: AchievementType,
    @Serializable(with = DatabaseModule.DateSerializer::class) val earnedAt: Date,
)

@Serializable
data class PermissionGroup(
    @SerialName("_id") val name: String,
    val color: TextColor = NamedTextColor.WHITE,
    val prefix: Component = Component.empty(),
    val permissions: List<@Serializable(with = DatabaseModule.PermissionSerializer::class) Permission> = emptyList(),
)
