package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game

/**
 * Called by the CountdownModule when the game starts.
 * This event cannot be canceled.
 */
class GameStartEvent(game: Game) : GameEvent(game)