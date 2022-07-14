package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.time.Duration

/**
 * Regenerates a player's health by 0.5 every second when they have been out of combat for at least 15 seconds.
 */
class NaturalRegenerationModule : GameModule() {

    private val combatStatus = hashMapOf<Player, Int>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(OldCombatModule.PlayerAttackEvent::class.java) { event ->
            if (event.target !is Player) return@addListener
            combatStatus[event.attacker] = 0
            combatStatus[event.target] = 0
        }
        eventNode.addListener(GameStartEvent::class.java) {
            parent.players.forEach { combatStatus[it] = 0 }
            MinecraftServer.getSchedulerManager().buildTask {
                for (s in combatStatus) {
                    combatStatus[s.key] = combatStatus.getOrDefault(s.key, 0) + 1
                    if (combatStatus[s.key]!! >= 15) s.key.health += 0.5f
                }
            }.repeat(Duration.ofSeconds(1)).schedule()
        }
    }
}