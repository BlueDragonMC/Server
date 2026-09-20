package com.bluedragonmc.server.module

import com.bluedragonmc.server.*
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

/**
 * Exposes the players that belong to this [com.bluedragonmc.server.ModuleHolder].
 */
class PlayerListModule(private val provider: () -> List<Player>) : GameModule(), PacketGroupingAudience {

    override val players: List<Player>
        get() = provider()

    override fun getPlayers(): Collection<Player> = players

    override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {}
}
