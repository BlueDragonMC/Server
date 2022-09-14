package com.bluedragonmc.server.module.combat

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.GameState
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.entity.damage.EntityProjectileDamage
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDeathEvent

class CustomDeathMessageModule : GameModule() {

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            if (parent.state != GameState.INGAME) {
                event.chatMessage = null
                return@addListener
            }
            val player = event.player
            event.chatMessage = when (val src = event.player.lastDamageSource) {
                is EntityDamage -> {
                    val playerName = (src.source as? Player)?.name
                    if (playerName != null) {
                        Component.translatable("death.attack.player", BRAND_COLOR_PRIMARY_2, player.name, playerName)
                    } else {
                        Component.translatable("death.attack.mob", BRAND_COLOR_PRIMARY_2, player.name, Component.translatable(src.source.entityType.registry().translationKey))
                    }
                }
                is EntityProjectileDamage -> {
                    val playerName = (src.shooter as? Player)?.name
                    if (playerName != null) {
                        Component.translatable("death.attack.arrow", BRAND_COLOR_PRIMARY_2, player.name, playerName)
                    } else if(src.shooter != null) {
                        Component.translatable("death.attack.arrow", BRAND_COLOR_PRIMARY_2, player.name, Component.translatable(src.shooter!!.entityType.registry().translationKey))
                    } else {
                        Component.translatable("death.attack.generic", BRAND_COLOR_PRIMARY_2, player.name)
                    }
                }
                DamageType.VOID -> Component.translatable("death.attack.outOfWorld", BRAND_COLOR_PRIMARY_2, player.name)
                DamageType.GRAVITY -> Component.translatable("death.attack.fall", BRAND_COLOR_PRIMARY_2, player.name)
                else -> Component.translatable("death.attack.generic", BRAND_COLOR_PRIMARY_2, player.name)
            }
        }
    }
}