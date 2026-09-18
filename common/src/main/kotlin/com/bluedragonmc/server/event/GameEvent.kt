package com.bluedragonmc.server.event

import com.bluedragonmc.server.GameContext

abstract class GameEvent(val game: GameContext) : Cancellable()