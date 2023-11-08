package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game

class CountdownEvent {
    class CountdownStartEvent(game: Game) : GameEvent(game)
    class CountdownTickEvent(game: Game, val secondsLeft: Int) : GameEvent(game)
}