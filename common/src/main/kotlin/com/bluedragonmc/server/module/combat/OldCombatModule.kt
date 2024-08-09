package com.bluedragonmc.server.module.combat

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.PlayerKillPlayerEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.MinecraftServer
import net.minestom.server.ServerFlag
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.*
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityPotionAddEvent
import net.minestom.server.event.entity.EntityTickEvent
import net.minestom.server.event.player.PlayerEatEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.trait.CancellableEvent
import net.minestom.server.event.trait.PlayerInstanceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.Material
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.network.packet.server.play.EntityAnimationPacket
import net.minestom.server.network.packet.server.play.EntityAnimationPacket.Animation
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.registry.DynamicRegistry
import net.minestom.server.tag.Tag
import kotlin.experimental.or
import kotlin.math.hypot

class OldCombatModule(var allowDamage: Boolean = true, var allowKnockback: Boolean = true) : GameModule() {

    companion object {
        val HURT_RESISTANT_TIME: Tag<Int> = Tag.Integer("hurt_resistant_time").defaultValue(0)
        val MAX_HURT_RESISTANT_TIME: Tag<Int> = Tag.Integer("max_hurt_resistant_time").defaultValue(20)
        val LAST_DAMAGE: Tag<Float> = Tag.Float("last_damage").defaultValue(0.0f)

        fun takeKnockback(xComponent: Double, zComponent: Double, target: Entity, multiplier: Double) {

            if (target.entityType == EntityType.ARMOR_STAND && target.isInvisible) return
            if (target.entityType == EntityType.ITEM_FRAME || target.entityType == EntityType.GLOW_ITEM_FRAME) return

            var xKnockback = xComponent
            var zKnockback = zComponent
            while (xKnockback * xKnockback + zKnockback * zKnockback < 0.0001) {
                xKnockback = (Math.random() - Math.random()) * 0.01
                zKnockback = (Math.random() - Math.random()) * 0.01
            }
            val magnitude = hypot(xComponent, zComponent)

            // see https://github.com/TogAr2/MinestomPvP/blob/4b2aa1e05b7a877ffe62183ed9b0b09088a7ca88/src/main/java/io/github/bloepiloepi/pvp/legacy/LegacyKnockbackSettings.java#L10
            // for more info on these constants
            val horizontal = ServerFlag.SERVER_TICKS_PER_SECOND * 0.8 * 0.4
            val vertical = (0.4 - 0.04) * ServerFlag.SERVER_TICKS_PER_SECOND
            val verticalLimit = 0.4 * ServerFlag.SERVER_TICKS_PER_SECOND
            val extra = multiplier + 1.0

            target.velocity = target.velocity.apply { x, y, z ->
                Vec(
                    x / 2.0 - if (magnitude == 0.0) 0.0 else (xKnockback / magnitude * horizontal) * extra,
                    (y / 2.0 + vertical).coerceAtMost(verticalLimit),
                    z / 2.0 - if (magnitude == 0.0) 0.0 else (zKnockback / magnitude * horizontal) * extra
                )
            }
        }
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {

        eventNode.addListener(EntityTickEvent::class.java) { event ->
            if (event.entity is LivingEntity) {
                val livingEntity = event.entity as LivingEntity
                val value = livingEntity.getTag(HURT_RESISTANT_TIME)
                if (value > 0) {
                    livingEntity.setTag(HURT_RESISTANT_TIME, value - 1)
                }
            }
            if (event.entity is Player) {
                val player = event.entity as Player
                player.activeEffects.forEach {
                    if (it.potion.effect == PotionEffect.REGENERATION) {
                        player.health += 1.0f / (50.0f / it.potion.amplifier)
                    }
                }
                if (player.activeEffects.none { it.potion.effect == PotionEffect.ABSORPTION }) {
                    player.additionalHearts = 0.0f
                }
            }
        }

        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            // Hint to client that there is no attack cooldown
            event.player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).baseValue = 100.0
            event.player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).baseValue = 1.0
        }

        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            // Reset attributes to default
            event.player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).baseValue = event.player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).attribute.defaultValue()
            event.player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).baseValue = event.player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).attribute.defaultValue()
            event.player.additionalHearts = 0.0f
        }

        eventNode.addListener(PlayerEatEvent::class.java) { event ->
            when(event.itemStack.material()) {
                Material.GOLDEN_APPLE -> {
                    event.player.addEffect(Potion(
                        PotionEffect.ABSORPTION, 1, 120 * 20, Potion.ICON_FLAG or Potion.PARTICLES_FLAG
                    ))
                    event.player.addEffect(Potion(
                        PotionEffect.REGENERATION, 2, 5 * 20, Potion.ICON_FLAG or Potion.PARTICLES_FLAG
                    ))
                }
                Material.ENCHANTED_GOLDEN_APPLE -> {
                    event.player.addEffect(Potion(
                        PotionEffect.ABSORPTION, 4, 120, Potion.ICON_FLAG or Potion.PARTICLES_FLAG
                    ))
                    event.player.addEffect(Potion(
                        PotionEffect.REGENERATION, 2, 5, Potion.ICON_FLAG or Potion.PARTICLES_FLAG
                    ))
                }
                else -> return@addListener
            }
            event.player.setItemInHand(event.hand, event.itemStack.withAmount(event.itemStack.amount() - 1))
        }

        eventNode.addListener(EntityPotionAddEvent::class.java) { event ->
            val entity = event.entity as? Player ?: return@addListener
            if (event.potion.effect == PotionEffect.ABSORPTION) {
                entity.additionalHearts = 4.0f * event.potion.amplifier
            }
        }

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            if (event.entity !is Player) return@addListener

            val playerAttackEvent = PlayerAttackEvent(
                event.instance,
                event.entity as Player,
                event.target
            ).apply(MinecraftServer.getGlobalEventHandler()::call)
            if (playerAttackEvent.isCancelled) return@addListener

            val player = event.entity as CustomPlayer
            val target = event.target

            // Spectators can't attack, and spectators and players in creative mode can't take damage
            if (player.gameMode == GameMode.SPECTATOR || (target is Player && (target.gameMode == GameMode.SPECTATOR || target.gameMode == GameMode.CREATIVE))) return@addListener

            // The player's base attack damage
            var dmgAttribute =
                player.getAttributeValue(Attribute.GENERIC_ATTACK_DAMAGE) + EnumItemDamage.ItemDamage.getAttackDamage(player.itemInMainHand.material())

            val heldEnchantments = player.inventory.itemInMainHand.get(ItemComponent.ENCHANTMENTS)?.enchantments ?: emptyMap<DynamicRegistry.Key<Enchantment>, Int>()
            // Extra damage provided by enchants like sharpness or smite
            val damageModifier = CombatUtils.getDamageModifier(heldEnchantments, target)

            val knockback = (heldEnchantments[Enchantment.KNOCKBACK]
                ?: 0) + if (player.isSprinting) 1 else 0

            if (dmgAttribute <= 0.0f && damageModifier <= 0.0f) return@addListener

            val shouldCrit =
                player.velocity.y < 0.0f && !player.isOnGround && !player.isOnLadder() && !player.isInWater() && !player.isBlind()
            if (shouldCrit && dmgAttribute > 0.0f) {
                dmgAttribute *= 1.5f
            }

            var damage = if (allowDamage) (dmgAttribute + damageModifier).toFloat() else 0.0f
            if (target is Player) damage = EnumArmorToughness.ArmorToughness.getReducedDamage(damage.toDouble(), target).toFloat()

            target.entityMeta.setNotifyAboutChanges(false)
            (player as Entity).entityMeta.setNotifyAboutChanges(false)

            if (target is LivingEntity) {
                if (target.getTag(HURT_RESISTANT_TIME) > target.getTag(MAX_HURT_RESISTANT_TIME) / 2.0f) {
                    // If this target has been attacked recently, and this interaction causes more damage
                    // than the previous hit, deal the difference in damage to the target.
                    // see the minecraft wiki: https://minecraft.fandom.com/wiki/Damage#Immunity
                    val lastDamage = target.getTag(LAST_DAMAGE)
                    if (damage > lastDamage) {
                        if ((damage - lastDamage) > target.health && target is Player && !target.isDead) {
                            parent.callCancellable(PlayerKillPlayerEvent(player, target)) {
                                target.damage(Damage(DamageType.PLAYER_ATTACK, player, player, player.position, damage - lastDamage))
                                target.setTag(LAST_DAMAGE, damage)
                            }
                        } else {
                            target.damage(Damage(DamageType.PLAYER_ATTACK, player, player, player.position, damage - lastDamage))
                            target.setTag(LAST_DAMAGE, damage)
                        }
                    } else return@addListener
                } else {
                    // The target has not been hit in the past (by default) 10 ticks.
                    if (damage > target.health && target is Player && !target.isDead) {
                        parent.callCancellable(PlayerKillPlayerEvent(player, target)) {
                            target.damage(Damage(DamageType.PLAYER_ATTACK, player, player, player.position, damage))
                            target.setTag(LAST_DAMAGE, damage)
                        }
                    } else {
                        target.damage(Damage(DamageType.PLAYER_ATTACK, player, player, player.position, damage))
                        target.setTag(LAST_DAMAGE, damage)
                        target.setTag(HURT_RESISTANT_TIME, target.getTag(MAX_HURT_RESISTANT_TIME))
                    }
                }
            }

            // Process fire aspect
            if (target is LivingEntity && (heldEnchantments[Enchantment.FIRE_ASPECT] ?: 0) > 0 && !target.isOnFire) {
                // Add 80 fire ticks per enchantment level - https://minecraft.wiki/w/Fire_Aspect#Usage
                target.fireTicks = heldEnchantments[Enchantment.FIRE_ASPECT]!! * 4 * ServerFlag.SERVER_TICKS_PER_SECOND
            }

            // Standard knockback that is unaffected by modifiers
            if (target is LivingEntity && allowKnockback) {
                takeKnockback(
                    player.position.x - target.getPosition().x,
                    player.position.z - target.getPosition().z,
                    target, knockback.toDouble()
                )
            }

            // Send crit particles
            if (shouldCrit) player.sendPacketToViewersAndSelf(
                EntityAnimationPacket(
                    target.entityId,
                    Animation.CRITICAL_EFFECT
                )
            )

            if (dmgAttribute > 0.0f) player.sendPacketToViewersAndSelf(
                EntityAnimationPacket(
                    target.entityId, Animation.MAGICAL_CRITICAL_EFFECT
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
                    val level = itemStack.get(ItemComponent.ENCHANTMENTS)?.enchantments?.get(Enchantment.THORNS) ?: return@forEach
                    if (CombatUtils.shouldCauseThorns(level)) {
                        val thornsDamage = CombatUtils.getThornsDamage(level)
                        target.damage(Damage(DamageType.THORNS, player, event.entity, event.entity.position, thornsDamage.toFloat()))
                        target.inventory.setEquipment(slot, CombatUtils.damageItemStack(itemStack, 2))
                    }
                }
            }

            target.entityMeta.setNotifyAboutChanges(true)
            (player as Entity).entityMeta.setNotifyAboutChanges(true)

            // MC 1.8 subtracts 0.3 from the player's food here; we will have to make our own tracker because Minestom keeps track of food as an integer
        }
    }

    data class PlayerAttackEvent(private val instance: Instance, val attacker: Player, val target: Entity) :
        PlayerInstanceEvent, CancellableEvent {

        private var cancelled: Boolean = false

        override fun getPlayer() = attacker

        override fun getInstance() = instance
        override fun isCancelled() = cancelled

        override fun setCancelled(cancel: Boolean) {
            cancelled = cancel
        }
    }
}
