package com.bluedragonmc.server.utils

import com.bluedragonmc.api.grpc.CommonTypes.EnumGameState

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
}