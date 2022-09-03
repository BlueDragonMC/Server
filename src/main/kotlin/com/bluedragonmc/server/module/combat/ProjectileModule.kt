package com.bluedragonmc.server.module.combat

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.MinecraftServer
import net.minestom.server.attribute.Attribute
import net.minestom.server.entity.EntityProjectile
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.metadata.arrow.ArrowMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.projectile.ProjectileCollideWithEntityEvent
import net.minestom.server.event.item.ItemUpdateStateEvent
import net.minestom.server.event.player.PlayerItemAnimationEvent
import net.minestom.server.item.Enchantment
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket
import net.minestom.server.tag.Tag
import java.time.Duration
import kotlin.math.ceil
import kotlin.random.Random

class ProjectileModule : GameModule() {

    companion object {
        private val CHARGE_START_TAG = Tag.Long("bow_charge_start").defaultValue(Long.MAX_VALUE)
        private val ARROW_DAMAGE_TAG = Tag.Short("entity_arrow_power").defaultValue(0) // the power enchantment level
        private val PUNCH_TAG = Tag.Short("entity_projectile_punch").defaultValue(0) // the punch enchantment level
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        hookBowEvents(eventNode)
    }

    /**
     * Hook events that allow players to shoot bows and be shot by arrows.
     * Implementation originally created for Minestom Arena:
     * https://github.com/Minestom/Arena/blob/master/src/main/java/net/minestom/arena/feature/BowFeature.java
     */
    private fun hookBowEvents(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerItemAnimationEvent::class.java) { event ->
            if (event.itemAnimationType == PlayerItemAnimationEvent.ItemAnimationType.BOW) {
                event.player.setTag(CHARGE_START_TAG, event.player.instance!!.worldAge)
            }
        }
        eventNode.addListener(ItemUpdateStateEvent::class.java) { event ->
            if (event.itemStack.material() != Material.BOW) return@addListener

            val secondsCharged =
                (event.player.instance!!.worldAge - event.player.getTag(CHARGE_START_TAG)).toFloat() / MinecraftServer.TICK_PER_SECOND
            val power = ((secondsCharged * secondsCharged + 2 * secondsCharged) / 2.0).coerceIn(0.0, 1.0)

            if (power > 0.2) {
                val projectile = EntityProjectile(event.player, EntityType.ARROW)
                if (power > 0.9) (projectile.entityMeta as ArrowMeta).isCritical = true
                projectile.scheduleRemove(Duration.ofSeconds(30))
                val eyePos = event.player.position.add(0.0, event.player.eyeHeight, 0.0)

                projectile.setInstance(event.player.instance!!, eyePos)
                projectile.shoot(eyePos.add(projectile.position.direction()).sub(0.0, 0.2, 0.0), power * 3.0, 1.0)
                projectile.setTag(PUNCH_TAG, event.itemStack.meta().enchantmentMap[Enchantment.PUNCH] ?: 0)
                projectile.setTag(ARROW_DAMAGE_TAG, event.itemStack.meta().enchantmentMap[Enchantment.POWER] ?: 0)
            }
            event.player.inventory.update()
        }
        eventNode.addListener(ProjectileCollideWithEntityEvent::class.java) { event ->
            val target = event.target as? LivingEntity ?: return@addListener
            val projectile = event.entity as EntityProjectile
            val arrowMeta = projectile.entityMeta as? ArrowMeta ?: return@addListener
            val shooter = projectile.shooter

            OldCombatModule.takeKnockback(-projectile.velocity.x,
                -projectile.velocity.z,
                target,
                projectile.getTag(PUNCH_TAG).toDouble())

            val damageModifier =
                (projectile.shooter as? LivingEntity)?.getAttribute(Attribute.ATTACK_DAMAGE)?.value ?: 1.0f

            var originalDamage = damageModifier * 2.0f + Random.nextFloat() * 0.25 + 0.15f
            if (projectile.getTag(ARROW_DAMAGE_TAG) > 0) {
                originalDamage += projectile.getTag(ARROW_DAMAGE_TAG) * 0.5 + 0.5
            }

            val baseDamage =
                ceil(projectile.velocity.length() / MinecraftServer.TICK_PER_SECOND * originalDamage).toFloat()
                    .coerceAtLeast(0.0f)

            val damage = if (arrowMeta.isCritical) baseDamage + Random.nextInt((baseDamage / 2.0 + 2.0).toInt())
            else baseDamage

            if (shooter is Player && target is Player && !arrowMeta.isSilent) {
                shooter.sendPacket(ChangeGameStatePacket(ChangeGameStatePacket.Reason.ARROW_HIT_PLAYER, 0.0f))
            }

            target.damage(DamageType.fromProjectile(projectile.shooter, projectile), damage)
            target.arrowCount++
            projectile.remove()
        }
    }
}
