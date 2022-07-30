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

    private val SEEN_TEAMS_TAG = Tag.String("seen_teams").list()
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
    }
}
