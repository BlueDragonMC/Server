package com.bluedragonmc.server.module.combat

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.withColor
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDeathEvent

class CustomDeathMessageModule : GameModule() {

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            val player = event.player
            event.chatMessage = when (val src = event.player.lastDamageSource) {
                is EntityDamage -> player.name + (" was killed by " withColor BRAND_COLOR_PRIMARY_2) + ((src.source as? Player)?.name
                    ?: (src.source.entityType.toString().lowercase() withColor BRAND_COLOR_PRIMARY_2))
                DamageType.VOID -> player.name + (" fell into the void." withColor BRAND_COLOR_PRIMARY_2)
                DamageType.GRAVITY -> player.name + (" fell from a high place." withColor BRAND_COLOR_PRIMARY_2)
                else -> player.name + (" died." withColor BRAND_COLOR_PRIMARY_2)
            }
        }
    }
}