package com.bluedragonmc.server.utils

import com.bluedragonmc.api.grpc.CommonTypes.EnumGameState
import com.bluedragonmc.api.grpc.CommonTypes.GameState as RpcGameState
import com.bluedragonmc.api.grpc.gameState

enum class GameState(val canPlayersJoin: Boolean) {
    SERVER_STARTING(false),
    WAITING(true),
    STARTING(true),
    INGAME(false),
    ENDING(false);

    fun mapToRpcState() = when (this) {
        SERVER_STARTING -> EnumGameState.INITIALIZING
        WAITING -> EnumGameState.WAITING
        STARTING -> EnumGameState.STARTING
        INGAME -> EnumGameState.INGAME
        ENDING -> EnumGameState.ENDING
    }

    /**
     * Builds the RPC representation of this game state for a game with [playerCount] players
     * and a capacity of [maxPlayers].
     */
    fun toRpcGameState(playerCount: Int, maxPlayers: Int): RpcGameState = gameState {
        gameState = mapToRpcState()
        openSlots = maxPlayers - playerCount
        joinable = canPlayersJoin
        maxSlots = maxPlayers
    }
}