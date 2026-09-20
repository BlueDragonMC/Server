package com.bluedragonmc.server.event

import com.bluedragonmc.server.*

abstract class GameEvent(internal val game: ModuleHolder) : Cancellable()