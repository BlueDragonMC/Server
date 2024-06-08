package com.bluedragonmc.server.utils.packet

import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.scoreboard.Team
import net.minestom.server.tag.Tag
import java.util.*

object GlowingEntityUtils {

    private val SEEN_TEAMS_TAG = Tag.String("seen_glow_teams").list()
    private val CURRENT_GLOW_TAG = Tag.String("current_glow_team")
    private val teamCache = mutableMapOf<NamedTextColor, Team>()

    fun glow(entity: Entity, color: NamedTextColor, viewers: Collection<Player>) {
        entity.isGlowing = true
        val team = teamCache.getOrPut(color) {
            MinecraftServer.getTeamManager().createBuilder(UUID.randomUUID().toString()).teamColor(color).build()
        }

        // Send the team creation packet to all viewers that have not been sent this packet before.
        PacketGroupingAudience.of(viewers.filter {
            !it.hasTag(SEEN_TEAMS_TAG) || !it.getTag(SEEN_TEAMS_TAG)!!.contains(team.teamName)
        }).sendGroupedPacket(team.createTeamsCreationPacket())

        PacketGroupingAudience.of(viewers).sendGroupedPacket(
            TeamsPacket(team.teamName, TeamsPacket.AddEntitiesToTeamAction(listOf(entity.uuid.toString())))
        )
        viewers.forEach { viewer ->
            if (!viewer.hasTag(SEEN_TEAMS_TAG)) {
                viewer.setTag(SEEN_TEAMS_TAG, listOf(team.teamName))
            } else {
                viewer.setTag(SEEN_TEAMS_TAG, viewer.getTag(SEEN_TEAMS_TAG) + team.teamName)
            }
        }
        entity.setTag(CURRENT_GLOW_TAG, team.teamName)
    }

    fun removeGlow(entity: Entity, viewers: Collection<Player>) {
        val team = entity.getTag(CURRENT_GLOW_TAG)
        PacketGroupingAudience.of(viewers).sendGroupedPacket(TeamsPacket(
            team, TeamsPacket.RemoveEntitiesToTeamAction(listOf(entity.uuid.toString()))
        ))
    }
}
