package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.*
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.MinecraftServer
import net.minestom.server.attribute.Attribute
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.*
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityTickEvent
import net.minestom.server.event.trait.CancellableEvent
import net.minestom.server.event.trait.PlayerInstanceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemStack
import net.minestom.server.network.packet.server.play.EntityAnimationPacket
import net.minestom.server.network.packet.server.play.EntityAnimationPacket.Animation
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class OldCombatModule(var allowDamage: Boolean = true, var allowKnockback: Boolean = true) : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {

        eventNode.addListener(EntityTickEvent::class.java) { event ->
            if (event.entity is LivingEntity) {
                val livingEntity = event.entity as LivingEntity
                if (livingEntity.hurtResistantTime > 0) {
                    livingEntity.hurtResistantTime--
                }
            }
        }

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener

            val playerAttackEvent = PlayerAttackEvent(event.instance, event.entity as Player, event.target)
                .apply(MinecraftServer.getGlobalEventHandler()::call)
            if(playerAttackEvent.isCancelled) return@addListener

            val player = event.entity as CustomPlayer
            val target = event.target

            // The player's base attack damage
            var dmgAttribute = player.getAttributeValue(Attribute.ATTACK_DAMAGE)

            val heldEnchantments = player.inventory.itemInMainHand.meta().enchantmentMap
            // Extra damage provided by enchants like sharpness or smite
            val damageModifier = getDamageModifier(heldEnchantments, target)

            val knockback = if (allowKnockback) (heldEnchantments[Enchantment.KNOCKBACK]
                ?: 0) + if (player.isSprinting) 1 else 0 else 0

            if (dmgAttribute <= 0.0f && damageModifier <= 0.0f) return@addListener

            val shouldCrit =
                player.velocity.y > 0.0f && !player.isOnGround && !player.isOnLadder() && !player.isInWater() && !player.isBlind()
            if (shouldCrit && dmgAttribute > 0.0f) {
                dmgAttribute *= 1.5f
            }

            val damage = if (allowDamage) dmgAttribute + damageModifier else 0.0f

            if (target is LivingEntity) {
                if (target.hurtResistantTime > target.maxHurtResistantTime / 2.0f) {
                    // If this target has been attacked recently, and this interaction causes more damage
                    // than the previous hit, deal the difference in damage to the target.
                    // see the minecraft wiki: https://minecraft.fandom.com/wiki/Damage#Immunity
                    if (damage > target.lastDamage) {
                        target.damage(DamageType.fromPlayer(player), damage - target.lastDamage)
                        target.lastDamage = damage
                    } else return@addListener
                } else {
                    // The target has not been hit in the past (by default) 10 ticks.
                    target.damage(DamageType.fromPlayer(player), damage)
                    target.lastDamage = damage
                    target.hurtResistantTime = target.maxHurtResistantTime
                    target.sendPacketToViewersAndSelf(EntityAnimationPacket(target.entityId, Animation.TAKE_DAMAGE))
                }
            }

            // Process fire aspect
            if (target is LivingEntity && (heldEnchantments[Enchantment.FIRE_ASPECT] ?: 0) > 0 && !target.isOnFire) {
                target.isOnFire = true
                target.setFireForDuration(heldEnchantments[Enchantment.FIRE_ASPECT]!! * 4)
            }

            // Standard knockback that is unaffected by modifiers
            if (target is LivingEntity) {
                var xKnockback: Double = player.position.x - target.getPosition().x
                var zKnockback: Double = player.position.z - target.getPosition().z

                while (xKnockback * xKnockback + zKnockback * zKnockback < 0.0001) {
                    xKnockback = (Math.random() - Math.random()) * 0.01
                    zKnockback = (Math.random() - Math.random()) * 0.01
                }
                val magnitude = hypot(xKnockback, zKnockback)

                // see https://github.com/TogAr2/MinestomPvP/blob/4b2aa1e05b7a877ffe62183ed9b0b09088a7ca88/src/main/java/io/github/bloepiloepi/pvp/legacy/LegacyKnockbackSettings.java#L10
                // for more info on these constants
                val horizontal = MinecraftServer.TICK_PER_SECOND * 0.8 * 0.4
                val vertical = (0.4 - 0.04) * MinecraftServer.TICK_PER_SECOND
                val verticalLimit = 0.4 * MinecraftServer.TICK_PER_SECOND

                target.velocity = target.velocity.apply { x, y, z ->
                    Vec(
                        x / 2.0 - (xKnockback  / magnitude * horizontal),
                        (y / 2.0 + vertical).coerceAtMost(verticalLimit),
                        z / 2.0 - (zKnockback / magnitude * horizontal)
                    )
                }
            }

            if (knockback > 0) {
                if (target is LivingEntity) {
                    target.takeKnockback(
                        knockback * 0.5f,
                        sin(Math.toRadians(player.position.yaw.toDouble() + 180.0)),
                        -cos(Math.toRadians(player.position.yaw.toDouble() + 180.0))
                    )
                } else {
                    target.velocity = target.velocity.add(
                        -sin(Math.toRadians(player.position.yaw.toDouble())) * knockback * 0.5f,
                        0.1,
                        cos(Math.toRadians(player.position.yaw.toDouble())) * knockback * 0.5f
                    )
                }
                player.isSprinting = false
            }

            // Send crit particles
            if (shouldCrit)
                player.sendPacketToViewersAndSelf(EntityAnimationPacket(target.entityId, Animation.CRITICAL_EFFECT))

            if (dmgAttribute > 0.0f)
                player.sendPacketToViewersAndSelf(
                    EntityAnimationPacket(
                        target.entityId,
                        Animation.MAGICAL_CRITICAL_EFFECT
                    )
                )

            if (target is Player) {
                val armor = listOf(
                    EquipmentSlot.HELMET to target.inventory.helmet,
                    EquipmentSlot.CHESTPLATE to target.inventory.chestplate,
                    EquipmentSlot.LEGGINGS to target.inventory.leggings,
                    EquipmentSlot.BOOTS to target.inventory.boots
                )
                armor.forEach { (slot, itemStack) ->
                    val level = itemStack.meta().enchantmentMap[Enchantment.THORNS]?.toInt() ?: return@forEach
                    if (shouldCauseThorns(level)) {
                        val thornsDamage = getThornsDamage(level)
                        player.damage(DamageType.fromPlayer(target), thornsDamage.toFloat())
                        player.inventory.setEquipment(slot, damageItemStack(itemStack, 2))
                    }
                }
            }

            // MC 1.8 subtracts 0.3 from the player's food here; we will have to make our own tracker because Minestom keeps track of food as an integer
        }
    }

    private fun damageItemStack(itemStack: ItemStack, amount: Int): ItemStack {
        val unbreakingLevel = itemStack.meta().enchantmentMap[Enchantment.UNBREAKING]?.toInt() ?: 0
        val processedAmount = (0 until amount).count { !shouldRestoreDurability(itemStack, unbreakingLevel) }

        return itemStack.withMeta { meta ->
            meta.damage(itemStack.meta().damage - processedAmount)
        }
    }

    private fun shouldRestoreDurability(itemStack: ItemStack, unbreakingLevel: Int): Boolean {
        // see https://minecraft.fandom.com/wiki/Unbreaking?so=search#Usage
        if (itemStack.material().isArmor) {
            if (Math.random() >= (0.6 + 0.4 / (unbreakingLevel + 1))) return false
        } else {
            if (Math.random() >= 1.0 / (unbreakingLevel + 1)) return false
        }
        return true
    }

    private fun shouldCauseThorns(level: Int): Boolean = if (level <= 0) false else Random.nextFloat() < 0.15 * level

    private fun getThornsDamage(level: Int): Int = if (level > 10) 10 - level else 1 + Random.nextInt(4)

    private fun getDamageModifier(enchants: Map<Enchantment, Short>, targetEntity: Entity): Float =
        if (enchants.containsKey(Enchantment.SHARPNESS)) {
            enchants[Enchantment.SHARPNESS]!! * 1.25f
        } else if (enchants.containsKey(Enchantment.SMITE) && isUndead(targetEntity)) {
            enchants[Enchantment.SMITE]!! * 2.5f
        } else if (enchants.containsKey(Enchantment.BANE_OF_ARTHROPODS) && isArthropod(targetEntity)) {
            enchants[Enchantment.BANE_OF_ARTHROPODS]!! * 2.5f
        } else 0.0f

    private fun isUndead(entity: Entity) = setOf(
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

    private fun isArthropod(entity: Entity) =
        entity.entityType == EntityType.SPIDER || entity.entityType == EntityType.CAVE_SPIDER || entity.entityType == EntityType.ENDERMITE || entity.entityType == EntityType.SILVERFISH

    data class PlayerAttackEvent(private val instance: Instance, val attacker: Player, val target: Entity) : PlayerInstanceEvent, CancellableEvent {

        private var cancelled: Boolean = false

        override fun getPlayer() = attacker

        override fun getInstance() = instance
        override fun isCancelled() = cancelled

        override fun setCancelled(cancel: Boolean) {
            cancelled = cancel
        }
    }
}
