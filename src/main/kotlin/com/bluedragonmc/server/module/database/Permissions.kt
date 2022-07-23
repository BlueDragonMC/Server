package com.bluedragonmc.server.module.database

import kotlinx.coroutines.runBlocking

object Permissions {

    fun hasPermission(playerData: PlayerDocument, node: String) = getPermission(playerData, node)

    fun getPermission(playerData: PlayerDocument, node: String) = runBlocking {
        val groupPermissions = playerData.getGroups().map { it.getAllPermissions() }.flatten()
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

    suspend fun getGroupByName(name: String) = DatabaseModule.getGroupsCollection().findOneById(name)

    suspend fun createGroup(group: PermissionGroup) {
        DatabaseModule.getGroupsCollection().insertOne(group)
    }

    suspend fun removeGroup(group: PermissionGroup) {
        DatabaseModule.getGroupsCollection().deleteOneById(group.name)
    }
}
