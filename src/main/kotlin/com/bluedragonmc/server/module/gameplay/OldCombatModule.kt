package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.*
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.attribute.Attribute
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.metadata.LivingEntityMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityTickEvent
import net.minestom.server.item.Enchantment
import net.minestom.server.network.packet.server.play.EntityAnimationPacket
import net.minestom.server.network.packet.server.play.EntityAnimationPacket.Animation
import kotlin.math.cos
import kotlin.math.sin

class OldCombatModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {

        eventNode.addListener(EntityTickEvent::class.java) { event ->
            if(event.entity is LivingEntity) {
                val livingEntity = event.entity as LivingEntity
                if(livingEntity.hurtResistantTime > 0) {
                    livingEntity.hurtResistantTime --
                }
            }
        }

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener

            val player = event.entity as CustomPlayer
            val target = event.target

            // The player's base attack damage
            var dmgAttribute = player.getAttributeValue(Attribute.ATTACK_DAMAGE)

            val heldEnchantments = player.inventory.itemInMainHand.meta().enchantmentMap
            // Extra damage provided by enchants like sharpness or smite
            val damageModifier = getDamageModifier(heldEnchantments, target)

            val knockback = (heldEnchantments[Enchantment.KNOCKBACK] ?: 0) + if (player.isSprinting) 1 else 0

            if (dmgAttribute <= 0.0f && damageModifier <= 0.0f) return@addListener

            val shouldCrit = player.fallDistance > 0.0f && !player.isOnGround && !player.isOnLadder() && !player.isInWater() && !player.isBlind()
            if(shouldCrit && dmgAttribute > 0.0f) {
                dmgAttribute *= 1.5f
            }

            val damage = dmgAttribute + damageModifier

            if (target is LivingEntity) {
                if (target.hurtResistantTime > target.maxHurtResistantTime / 2.0f) {
                    if(damage > target.lastDamage) {
                        target.damage(DamageType.fromPlayer(player), damage - target.lastDamage)
                        target.lastDamage = damage
                    }
                } else {
                    target.damage(DamageType.fromPlayer(player), damage)
                    target.lastDamage = damage
                    target.hurtResistantTime = target.maxHurtResistantTime
                    target.sendPacketToViewersAndSelf(EntityAnimationPacket(target.entityId, Animation.TAKE_DAMAGE))
                }
            }

            // Process fire aspect
            if(target.entityMeta is LivingEntityMeta && (heldEnchantments[Enchantment.FIRE_ASPECT] ?: 0) > 0 && !target.isOnFire) {
                target.isOnFire = true
            }

            if(knockback > 0) {
                target.velocity = target.velocity.add(
                    -sin(Math.toRadians(player.position.yaw.toDouble())) * knockback * 0.5f,
                    0.1,
                    cos(Math.toRadians(player.position.yaw.toDouble())) * knockback * 0.5f
                )
                // MC 1.8 applies motion to the attacker here; this may only be for singleplayer
                player.isSprinting = false
            }

            // Send crit particles
            if(shouldCrit)
                player.sendPacketToViewersAndSelf(EntityAnimationPacket(target.entityId, Animation.CRITICAL_EFFECT))

            if(dmgAttribute > 0.0f)
                player.sendPacketToViewersAndSelf(EntityAnimationPacket(target.entityId, Animation.MAGICAL_CRITICAL_EFFECT))

            if(target is Player) {
                val armor = listOf(target.inventory.helmet, target.inventory.chestplate, target.inventory.leggings, target.inventory.boots)
                // TODO: Process thorns
            }

            // MC 1.8 subtracts 0.3 from the player's food here; we will have to make our own tracker because Minestom keeps track of food as an integer
        }
    }

    fun getDamageModifier(enchants: Map<Enchantment, Short>, targetEntity: Entity): Float =
        if (enchants.containsKey(Enchantment.SHARPNESS)) {
            enchants[Enchantment.SHARPNESS]!! * 1.25f
        } else if (enchants.containsKey(Enchantment.SMITE) && isUndead(targetEntity)) {
            enchants[Enchantment.SMITE]!! * 2.5f
        } else if (enchants.containsKey(Enchantment.BANE_OF_ARTHROPODS) && isArthropod(targetEntity)) {
            enchants[Enchantment.BANE_OF_ARTHROPODS]!! * 2.5f
        } else 0.0f

    fun isUndead(entity: Entity) = setOf(
        EntityType.DROWNED,
        EntityType.HUSK,
        EntityType.PHANTOM,
        EntityType.SKELETON,
        EntityType.SKELETON_HORSE,
        EntityType.STRAY,
        EntityType.WITHER,
        EntityType.WITHER_SKELETON,
        EntityType.ZOGLIN,
        EntityType.ZOMBIE,
        EntityType.ZOMBIE_VILLAGER,
        EntityType.ZOMBIFIED_PIGLIN,
    ).contains(entity.entityType)

    fun isArthropod(entity: Entity) =
        entity.entityType == EntityType.SPIDER || entity.entityType == EntityType.CAVE_SPIDER || entity.entityType == EntityType.ENDERMITE || entity.entityType == EntityType.SILVERFISH

}
