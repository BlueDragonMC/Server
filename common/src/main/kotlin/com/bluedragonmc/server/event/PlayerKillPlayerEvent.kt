package com.bluedragonmc.server.event

import net.minestom.server.entity.Player

/**
 * Can be called by a module when a player has killed another player.
 */
class PlayerKillPlayerEvent(val attacker: Player, val target: Player) : CancellablePlayerEvent(attacker)