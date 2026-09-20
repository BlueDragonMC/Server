package com.bluedragonmc.server.event

import com.bluedragonmc.server.*

abstract class GameEvent(val game: ModuleHolder) : Cancellable()