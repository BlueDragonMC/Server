package com.bluedragonmc.server.module

import com.bluedragonmc.server.ModuleHolder
import com.bluedragonmc.server.event.PlayerJoinGameEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerPacketOutEvent
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket
import java.util.*

/**
 * Scopes this game's tab list to the players in this game.
 */
@DependsOn(PlayerListModule::class)
class ScopedTabListModule : GameModule() {

    override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerJoinGameEvent::class.java) { event -> onJoin(event.player) }
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event -> onLeave(event.player) }
        eventNode.addListener(PlayerPacketOutEvent::class.java, ::filterPacket)
    }

    private fun onJoin(player: Player) {
        val others = players.filter { it.uuid != player.uuid }

        // Show every other member of the game to the joining player.
        if (others.isNotEmpty()) player.sendPacket(addPacket(others))

        // Show the joining player to every other member of the game.
        if (others.isNotEmpty()) {
            val packet = addPacket(listOf(player))
            others.forEach { it.sendPacket(packet) }
        }

        // Remove any players from other games that might currently appear in the player's tab list
        removePlayersFromOtherGames(player)
    }

    private fun onLeave(player: Player) {
        val others = players.filter { it.uuid != player.uuid }

        // Remove the leaving player from the remaining members' tab lists.
        if (others.isNotEmpty()) {
            val removePacket = PlayerInfoRemovePacket(player.uuid)
            others.forEach { it.sendPacket(removePacket) }
        }

        // Clear the leaving player's tab list of the game they just left.
        if (others.isNotEmpty()) player.sendPacket(PlayerInfoRemovePacket(others.map { it.uuid }))
    }

    /**
     * Filters a tab list packet that is about to be sent to a member of this game.
     *
     * Runs on the connection's write thread.
     */
    private fun filterPacket(event: PlayerPacketOutEvent) {
        val packet = event.packet
        if (packet is PlayerInfoUpdatePacket) {
            val recipient = event.player
            val containsForeignPlayer = packet.entries.any { entry ->
                entry.uuid != recipient.uuid // The recipient's own entry is always allowed, since the client needs it for their own skin.
                        && players.none { it.uuid == entry.uuid }
            }
            if (containsForeignPlayer) event.isCancelled = true
        }
        // Removals are always safe to forward: removing a player the client does not know about is a no-op.
        // Letting them through also guarantees that a player who disconnects is removed from every tab list.
    }

    private fun removePlayersFromOtherGames(player: Player) {
        val toRemove = MinecraftServer.getConnectionManager().onlinePlayers
            .mapTo(mutableSetOf()) { it.uuid }
            .subtract(players.map { it.uuid }.toSet())
            .toList()
        if (toRemove.isNotEmpty()) player.sendPacket(PlayerInfoRemovePacket(toRemove))
    }

    private fun addPacket(players: List<Player>): PlayerInfoUpdatePacket = PlayerInfoUpdatePacket(
        EnumSet.allOf(PlayerInfoUpdatePacket.Action::class.java),
        players.map(::addEntry),
    )

    private fun addEntry(player: Player) = PlayerInfoUpdatePacket.Entry(
        player.uuid,
        player.username,
        player.skin?.let {
            listOf(PlayerInfoUpdatePacket.Property("textures", it.textures(), it.signature()))
        } ?: emptyList(),
        player.isListed,
        player.latency,
        player.gameMode,
        player.displayName,
        null,
        player.listOrder,
        true,
    )
}
