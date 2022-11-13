package com.bluedragonmc.server.module.database

import com.bluedragonmc.server.Database
import kotlinx.coroutines.runBlocking
import java.util.*

object Permissions {

    private val serverOwners = listOf(
        UUID.fromString("110429e8-197f-4446-8bec-5d66f17be4d5"),
        UUID.fromString("a0048143-460f-417e-9119-f30eb9674a7a")
    )

    fun hasPermission(playerData: PlayerDocument, node: String) = serverOwners.contains(playerData.uuid) || getPermission(playerData, node)

    fun getPermission(playerData: PlayerDocument, node: String) = runBlocking {
        val groupPermissions = playerData.getGroups().flatMap { it.getAllPermissions() }
        val playerPermissions = playerData.permissions
        return@runBlocking (playerPermissions + groupPermissions).contains(node) // TODO wildcard permissions
    }

    suspend fun setPermission(doc: PlayerDocument, node: String, value: Boolean) {
        doc.update(PlayerDocument::permissions, (doc.permissions + node).toMutableList())
    }

    suspend fun removePermission(doc: PlayerDocument, node: String) {
        doc.update(PlayerDocument::permissions, doc.permissions.apply {
            remove(node)
        })
    }

    suspend fun getGroups(player: PlayerDocument) = player.getGroups()

    fun isInGroup(doc: PlayerDocument, groupName: String) = doc.groups.contains(groupName)

    suspend fun addGroup(doc: PlayerDocument, groupName: String) {
        doc.update(PlayerDocument::groups, (doc.groups + groupName).toMutableList())
    }

    suspend fun removeGroup(doc: PlayerDocument, groupName: String) {
        doc.update(PlayerDocument::groups, (doc.groups - groupName).toMutableList())
    }

    suspend fun getGroupByName(name: String) = Database.connection.getGroupByName(name)

    suspend fun createGroup(group: PermissionGroup) {
        Database.connection.insertGroup(group)
    }

    suspend fun removeGroup(group: PermissionGroup) {
        Database.connection.removeGroup(group)
    }

    suspend fun setPermission(group: PermissionGroup, node: String, value: Boolean) {
        group.update(PermissionGroup::permissions, (group.permissions + node).toMutableList())
    }

    suspend fun removePermission(group: PermissionGroup, node: String) {
        group.update(PermissionGroup::permissions, (group.permissions - node).toMutableList())
    }
}
