package com.bluedragonmc.server.module.vanilla

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.projectile.FireworkRocketMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.EntityStatusPacket
import net.minestom.server.sound.SoundEvent
import net.minestom.server.utils.time.TimeUnit
import java.time.Duration

/**
 * Vanilla-like functionality for firework rockets.
 * The particles depend on the properties of the rocket.
 * If [boostElytra] is enabled, using a firework rocket will
 * increase the velocity of a player flying with elytra.
 */
class FireworkRocketModule(private val boostElytra: Boolean = true) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            // Firework particles
            if (event.itemStack.material() != Material.FIREWORK_ROCKET) return@addListener
            val firework = Entity(EntityType.FIREWORK_ROCKET)
            (firework.entityMeta as FireworkRocketMeta).fireworkInfo = event.itemStack
            firework.setNoGravity(true)
            firework.setInstance(event.instance, event.player.position)
            event.instance.playSound(
                Sound.sound(
                    SoundEvent.ENTITY_FIREWORK_ROCKET_LAUNCH, Sound.Source.PLAYER, 2.0f, 1.0f
                ), event.player.position
            )
            MinecraftServer.getSchedulerManager().buildTask {
                event.instance.sendGroupedPacket(EntityStatusPacket(firework.entityId, 17))
                firework.remove()
            }.delay(Duration.ofSeconds(1)).schedule()
            // Elytra boost
            if (boostElytra) {
                event.player.setItemInHand(event.hand, event.itemStack.withAmount(event.itemStack.amount()-1))
                elytraBoostPlayer(event.player)
            }
        }
    }

    fun elytraBoostPlayer(player: Player) {
        val velocityTask = MinecraftServer.getSchedulerManager().buildTask {
            player.velocity = player.position.direction().mul(30.0)
        }.repeat(1, TimeUnit.SERVER_TICK).schedule()

        MinecraftServer.getSchedulerManager().buildTask {
            velocityTask.cancel()
        }.delay(30, TimeUnit.SERVER_TICK).schedule()
    }

}