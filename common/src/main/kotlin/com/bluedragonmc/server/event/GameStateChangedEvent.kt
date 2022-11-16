package com.bluedragonmc.server.event

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.utils.GameState

/**
 * Called when a game's state is changed.
 * Used to propagate state updates to external services,
 * such as with the MessagingModule.
 */
class GameStateChangedEvent(game: Game, val oldState: GameState, val newState: GameState) : GameEvent(game) {
    val rpcGameState: CommonTypes.GameState
        get() {
            return CommonTypes.GameState.newBuilder()
                .setGameState(newState.mapToRpcState())
                .setJoinable(newState.canPlayersJoin)
                .setOpenSlots(game.maxPlayers - game.players.size)
                .build()
        }
}