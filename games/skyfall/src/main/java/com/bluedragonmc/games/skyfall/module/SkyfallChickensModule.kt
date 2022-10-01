package com.bluedragonmc.games.skyfall.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.vanilla.FireworkRocketModule
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

/**
 * Spawns chickens at the [chickenLocations] when the game starts.
 * When a player flying with an elytra punches a chicken,
 * the player gains a speed boost.
 */
class SkyfallChickensModule(private val chickenLocations: List<Point> = listOf()) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(GameStartEvent::class.java) { event ->
            chickenLocations.forEach { location ->
                Entity(EntityType.CHICKEN).apply {
                    setInstance(parent.getInstance(), location)
                    setNoGravity(true)
                }
            }
        }
        eventNode.addListener(OldCombatModule.PlayerAttackEvent::class.java) { event ->
            if (event.target.entityType != EntityType.CHICKEN) return@addListener
            if (event.player.isFlyingWithElytra) parent.getModule<FireworkRocketModule>().elytraBoostPlayer(event.player)
            else event.player.sendActionBar("Hold SPACE to glide with your elytra!" withColor NamedTextColor.RED) // TODO make this use translatable and keybind-specific components
            event.isCancelled = true // No damage to the chicken
        }
    }
}