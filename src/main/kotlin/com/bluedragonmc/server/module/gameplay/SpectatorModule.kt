package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.SingleAssignmentProperty
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDeathEvent

/**
 * A simple module to manage spectators in a game.
 */
class SpectatorModule(var spectateOnDeath: Boolean) : GameModule() {
    private val spectators = mutableListOf<Player>()
    private var parent by SingleAssignmentProperty<Game>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            if (spectateOnDeath && !isSpectating(event.player)) addSpectator(event.player)
        }
    }

    /**
     * Adds the specified player as a spectator and sets their game mode to spectator.
     * When a player is a spectator, they are considered to be "out of the game".
     */
    fun addSpectator(player: Player) {
        spectators.add(player)
        player.gameMode = GameMode.SPECTATOR
        parent.callEvent(StartSpectatingEvent(parent, player))
    }

    /**
     * Removes the specified player as a spectator. Does not change their game mode.
     */
    fun removeSpectator(player: Player) {
        spectators.remove(player)
        parent.callEvent(StopSpectatingEvent(parent, player))
    }

    /**
     * Returns true if the specified player is a spectator, false otherwise.
     */
    fun isSpectating(player: Player): Boolean = spectators.contains(player)

    /**
     * Returns the number of spectators.
     */
    fun spectatorCount() = spectators.size

    /**
     * This event is fired when a player becomes a spectator.
     */
    class StartSpectatingEvent(game: Game, player: Player): GameEvent(game)

    /**
     * This event is fired when a player stops being a spectator.
     */
    class StopSpectatingEvent(game: Game, player: Player): GameEvent(game)
}