package com.bluedragonmc.server.event

import com.bluedragonmc.server.GameContext

class CountdownEvent {
    class CountdownStartEvent(game: GameContext) : GameEvent(game)
    class CountdownTickEvent(game: GameContext, val secondsLeft: Int) : GameEvent(game)
}