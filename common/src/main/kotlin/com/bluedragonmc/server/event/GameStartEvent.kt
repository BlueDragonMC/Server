package com.bluedragonmc.server.event

import com.bluedragonmc.server.GameContext

/**
 * Called by the CountdownModule when the game starts.
 * This event cannot be canceled.
 */
class GameStartEvent(game: GameContext) : GameEvent(game)