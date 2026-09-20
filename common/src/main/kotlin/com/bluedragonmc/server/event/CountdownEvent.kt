package com.bluedragonmc.server.event

import com.bluedragonmc.server.*

class CountdownEvent {
    class CountdownStartEvent(game: ModuleHolder) : GameEvent(game)
    class CountdownTickEvent(game: ModuleHolder, val secondsLeft: Int) : GameEvent(game)
}