package com.bluedragonmc.server.event

import com.bluedragonmc.server.*

/**
 * Called by the CountdownModule when the game starts.
 * This event cannot be canceled.
 */
class GameStartEvent(game: ModuleHolder) : GameEvent(game)