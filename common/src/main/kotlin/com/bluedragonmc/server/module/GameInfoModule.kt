package com.bluedragonmc.server.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.*
import com.bluedragonmc.server.game.GameData
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

/**
 * Exposes immutable metadata about this holder's game.
 */
class GameInfoModule(private val game: Game) : GameModule() {

    override val id: String
        get() = game.id

    override val data: GameData
        get() = game.data

    /** The maximum number of players allowed in this game. */
    val maxPlayers: Int
        get() = game.maxPlayers

    override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {}
}
