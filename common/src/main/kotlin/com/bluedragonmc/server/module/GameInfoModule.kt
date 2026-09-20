package com.bluedragonmc.server.module

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.*
import com.bluedragonmc.server.game.GameData
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

/**
 * Exposes immutable metadata about this holder's game.
 */
class GameInfoModule(private val game: Game) : GameModule() {

    val id: String
        get() = game.id

    val data: GameData
        get() = game.data

    val rpcGameState: CommonTypes.GameState
        get() = game.rpcGameState

    override fun initialize(parent: ModuleHolder, eventNode: EventNode<Event>) {}
}
