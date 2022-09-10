package com.bluedragonmc.server.event

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.minigame.KitsModule
import net.minestom.server.entity.Player

/**
 * This event is fired when the player confirms their kit selection.
 * If the player closes the kit selection menu, this event is not fired.
 */
class KitSelectedEvent(game: Game, val player: Player, val kit: KitsModule.Kit) : GameEvent(game)