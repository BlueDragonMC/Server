package com.bluedragonmc.server.utils

enum class GameState(val canPlayersJoin: Boolean) {
    SERVER_STARTING(false),
    WAITING(true),
    STARTING(true),
    INGAME(false),
    ENDING(false)
}