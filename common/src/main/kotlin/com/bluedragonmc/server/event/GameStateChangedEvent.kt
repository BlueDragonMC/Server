package com.bluedragonmc.server.event

import com.bluedragonmc.server.*
import com.bluedragonmc.server.utils.GameState

/**
 * Called when a game's state is changed.
 * Used to propagate state updates to external services,
 * such as with the MessagingModule.
 */
class GameStateChangedEvent(game: ModuleHolder, val oldState: GameState, val newState: GameState) : GameEvent(game)
