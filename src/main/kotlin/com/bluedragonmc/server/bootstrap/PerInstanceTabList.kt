package com.bluedragonmc.server.bootstrap

import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.AddEntityToInstanceEvent
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent
import net.minestom.server.network.packet.server.play.PlayerInfoPacket
import net.minestom.server.network.packet.server.play.PlayerInfoPacket.AddPlayer
import net.minestom.server.network.packet.server.play.PlayerInfoPacket.AddPlayer.Property
import net.minestom.server.network.packet.server.play.PlayerInfoPacket.RemovePlayer
import net.minestom.server.utils.PacketUtils

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
            PacketUtils.sendGroupedPacket(
                newInstance.players + player,
                getAddPlayerPacket(player)
            )
        }
        eventNode.addListener(RemoveEntityFromInstanceEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener
            val player = event.entity as Player
            // Remove this player from everyone's tablist
            PacketUtils.sendGroupedPacket(
                event.instance.players - player,
                getRemovePlayerPacket(player)
            )
        }
    }

    private fun getAddPlayerPacket(players: Iterable<Player>) =
        PlayerInfoPacket(PlayerInfoPacket.Action.ADD_PLAYER, players.map { getAddPlayerEntry(it) })

    private fun getRemovePlayerPacket(players: Iterable<Player>) =
        PlayerInfoPacket(PlayerInfoPacket.Action.REMOVE_PLAYER, players.map { getRemovePlayerEntry(it) })

    private fun getAddPlayerPacket(player: Player) =
        PlayerInfoPacket(
            PlayerInfoPacket.Action.ADD_PLAYER,
            getAddPlayerEntry(player)
        )

    private fun getAddPlayerEntry(player: Player) = AddPlayer(
        player.uuid,
        player.username,
        if (player.skin != null) listOf(
            Property(
                "textures",
                player.skin!!.textures(),
                player.skin!!.signature()
            )
        ) else emptyList(),
        player.gameMode,
        player.latency,
        player.name,
        player.playerConnection.playerPublicKey()
    )

    private fun getRemovePlayerPacket(player: Player) =
        PlayerInfoPacket(PlayerInfoPacket.Action.REMOVE_PLAYER, getRemovePlayerEntry(player))

    private fun getRemovePlayerEntry(player: Player) = RemovePlayer(player.uuid)
}