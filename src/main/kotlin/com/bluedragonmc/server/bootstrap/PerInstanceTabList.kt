package com.bluedragonmc.server.bootstrap

import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.AddEntityToInstanceEvent
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import net.minestom.server.utils.PacketSendingUtils
import java.util.*

object PerInstanceTabList : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        eventNode.addListener(AddEntityToInstanceEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener
            val player = event.entity as Player
            val previousInstance = player.instance
            val newInstance = event.instance
            // Remove all players from the player's tablist (not necessary on first join)
            if (previousInstance != null) {
                player.sendPacket(getRemovePlayerPacket(previousInstance.players - player))
            }
            // Add all the instance's current players to the joining player's tablist
            player.sendPacket(
                getAddPlayerPacket(newInstance.players)
            )
            // Send a packet to all players in the instance to add this new player
            PacketSendingUtils.sendGroupedPacket(
                newInstance.players + player,
                getAddPlayerPacket(player)
            )
        }
        eventNode.addListener(RemoveEntityFromInstanceEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener
            val player = event.entity as Player
            // Remove this player from everyone's tablist
            PacketSendingUtils.sendGroupedPacket(
                event.instance.players - player,
                getRemovePlayerPacket(setOf(player))
            )
        }
    }

    private fun getAddPlayerPacket(players: Iterable<Player>) =
        PlayerInfoUpdatePacket(
            EnumSet.of(
                PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                PlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                PlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
            ),
            players.map { getAddPlayerEntry(it) })

    private fun getRemovePlayerPacket(players: Iterable<Player>) =
        PlayerInfoRemovePacket(players.map { it.uuid })

    private fun getAddPlayerPacket(player: Player) =
        PlayerInfoUpdatePacket(
            EnumSet.of(
                PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                PlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                PlayerInfoUpdatePacket.Action.UPDATE_LISTED
            ),
            listOf(getAddPlayerEntry(player))
        )

    private fun getAddPlayerEntry(player: Player) = PlayerInfoUpdatePacket.Entry(
        player.uuid,
        player.username,
        if (player.skin != null) listOf(
            PlayerInfoUpdatePacket.Property(
                "textures",
                player.skin!!.textures(),
                player.skin!!.signature()
            )
        ) else emptyList(),
        true,
        player.latency,
        player.gameMode,
        player.name,
        null,
        1024
    )
}