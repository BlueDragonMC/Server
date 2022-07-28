package com.bluedragonmc.server.utils.packet

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.scoreboard.Team


object GlowingEntityUtils {
    private val glowTeams = mutableMapOf<Entity, Team>()

    fun glow(entity: Entity, color: NamedTextColor) {
        entity.isGlowing = true
        val team = glowTeams.getOrPut(entity) {
            MinecraftServer.getTeamManager().createTeam(
                entity.uuid.toString(), Component.empty(), Component.empty(), NamedTextColor.WHITE, Component.empty()
            ).apply { addMember(entity.uuid.toString()) }
        }
        if(team.teamColor != color) {
            team.updateTeamColor(color)
        }
    }

    fun cleanup(entity: Entity) {
        glowTeams.remove(entity)?.let { team ->
            MinecraftServer.getTeamManager().deleteTeam(team)
        }
    }
}