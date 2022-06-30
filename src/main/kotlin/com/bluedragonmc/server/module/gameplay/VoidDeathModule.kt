package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent

class VoidDeathModule(private val threshold: Double) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if(event.player.position.y < threshold) {
                event.player.damage(DamageType.VOID, 20.0f)
                event.player.kill()
            }
        }
    }
}