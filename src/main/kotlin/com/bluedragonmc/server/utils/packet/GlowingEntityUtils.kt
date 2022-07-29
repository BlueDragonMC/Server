package com.bluedragonmc.server.utils.packet

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.scoreboard.Team
import net.minestom.server.tag.Tag
import java.util.*

object GlowingEntityUtils {

    private val TEAM_NAME_TAG = Tag.String("entity_team_name")
    private val teamCache = mutableMapOf<NamedTextColor, Team>()

    fun glow(entity: Entity, color: NamedTextColor) {
        entity.isGlowing = true
        val team = teamCache.getOrPut(color) {
            MinecraftServer.getTeamManager().createTeam(
                UUID.randomUUID().toString(), Component.empty(), Component.empty(), color, Component.empty()
            )
        }
        val uuid = entity.uuid.toString()
        if (entity.hasTag(TEAM_NAME_TAG)) {
            val currentTeam = MinecraftServer.getTeamManager().getTeam(entity.getTag(TEAM_NAME_TAG))
            if (currentTeam != team) currentTeam.removeMember(uuid)
        }
        if (!team.members.contains(uuid)) {
            team.addMember(uuid)
            entity.setTag(TEAM_NAME_TAG, team.teamName)
        }
    }
}
