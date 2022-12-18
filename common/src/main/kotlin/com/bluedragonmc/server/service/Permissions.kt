package com.bluedragonmc.server.service

import com.bluedragonmc.server.api.PermissionManager
import java.util.*

object Permissions {

    lateinit var manager: PermissionManager
        private set

    fun initialize(manager: PermissionManager) {
        this.manager = manager
    }

    fun hasPermission(player: UUID, node: String) = manager.hasPermission(player, node)
    fun getMetadata(player: UUID) = manager.getMetadata(player)
}
