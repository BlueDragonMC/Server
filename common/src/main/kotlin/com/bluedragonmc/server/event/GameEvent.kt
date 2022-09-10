package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game

abstract class GameEvent(val game: Game) : Cancellable()