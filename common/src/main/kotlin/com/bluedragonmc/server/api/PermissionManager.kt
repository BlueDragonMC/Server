package com.bluedragonmc.server.api

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import java.util.*

interface PermissionManager {
    fun getMetadata(player: UUID): PlayerMeta
    fun hasPermission(player: UUID, node: String): Boolean?
}

data class PlayerMeta(val prefix: Component, val suffix: Component, val primaryGroup: String, val rankColor: TextColor)
