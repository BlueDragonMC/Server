package com.bluedragonmc.server.module.combat

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.MapUtils
import net.minestom.server.MinecraftServer
import net.minestom.server.attribute.Attribute
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.*
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.metadata.arrow.ArrowMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent
import net.minestom.server.event.entity.projectile.ProjectileCollideWithEntityEvent
import net.minestom.server.event.item.ItemUpdateStateEvent
import net.minestom.server.event.player.PlayerItemAnimationEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Explosion
import net.minestom.server.instance.Instance
import net.minestom.server.inventory.TransactionOption
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

        private val SNOWBALL_DAMAGE_TYPE = DamageType("snowball")
        private val EGG_DAMAGE_TYPE = DamageType("egg")
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        hookBowEvents(eventNode)
        hookSnowballEvents(eventNode)
        hookEggEvents(eventNode)
        hookFireballEvents(eventNode)
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

            if (event.player.gameMode != GameMode.CREATIVE) {
                if (!takeOne(event.player, Material.ARROW)) return@addListener
            }

            val secondsCharged =
                (event.player.instance!!.worldAge - event.player.getTag(CHARGE_START_TAG)).toFloat() / MinecraftServer.TICK_PER_SECOND
            val power = ((secondsCharged * secondsCharged + 2 * secondsCharged) / 2.0).coerceIn(0.0, 1.0)

            if (power > 0.2) {
                val projectile = EntityProjectile(event.player, EntityType.ARROW)
                if (power > 0.9) (projectile.entityMeta as ArrowMeta).isCritical = true
                projectile.scheduleRemove(Duration.ofSeconds(30))
                val eyePos = getEyePos(event.player)

                projectile.setInstance(event.player.instance!!, eyePos)
                projectile.shoot(getLaunchPos(event.player), power * 3.0, 1.0)
                projectile.setTag(PUNCH_TAG, event.itemStack.meta().enchantmentMap[Enchantment.PUNCH] ?: 0)
                projectile.setTag(ARROW_DAMAGE_TAG, event.itemStack.meta().enchantmentMap[Enchantment.POWER] ?: 0)
            }
        }
        eventNode.addListener(ProjectileCollideWithEntityEvent::class.java) { event ->
            val target = event.target as? LivingEntity ?: return@addListener
            val projectile = event.entity as EntityProjectile

            if (projectile.entityType != EntityType.ARROW) return@addListener

            val arrowMeta = projectile.entityMeta as? ArrowMeta ?: return@addListener
            val shooter = projectile.shooter

            if (target is Player) {
                // Prevent players in creative and spectator modes from being hit by arrows
                if (target.gameMode == GameMode.CREATIVE || target.gameMode == GameMode.SPECTATOR) return@addListener
                if (target.isInvincible()) return@addListener
            }

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
            (target as? Player)?.resetInvincibilityPeriod()
        }
    }

    private fun getEyePos(player: Player): Pos {
        return player.position.add(0.0, player.eyeHeight, 0.0)
    }

    private fun hookSnowballEvents(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            val itemStack = event.player.getItemInHand(event.hand)
            if (itemStack.material() == Material.SNOWBALL) {

                if (event.player.gameMode != GameMode.CREATIVE) {
                    event.player.inventory.setItemInHand(event.hand, itemStack.withAmount(itemStack.amount() - 1))
                }

                // Shoot a snowball from the player's position
                val snowball = EntityProjectile(event.player, EntityType.SNOWBALL)
                snowball.setInstance(event.instance, getEyePos(event.player))
                snowball.shoot(getLaunchPos(event.player), 3.0, 1.0)
                snowball.scheduleRemove(Duration.ofSeconds(30))
            }
        }
        eventNode.addListener(ProjectileCollideWithEntityEvent::class.java) { event ->
            val target = event.target as? LivingEntity ?: return@addListener
            val projectile = event.entity as EntityProjectile

            if (projectile.entityType != EntityType.SNOWBALL) return@addListener

            if ((target as? Player)?.isInvincible() == true) return@addListener

            OldCombatModule.takeKnockback(-projectile.velocity.x, -projectile.velocity.z, target, 0.0)
            target.damage(SNOWBALL_DAMAGE_TYPE, 0.0f)
            projectile.remove()
            (target as? Player)?.resetInvincibilityPeriod()
        }
        eventNode.addListener(ProjectileCollideWithBlockEvent::class.java) { event ->
            if (event.entity.entityType == EntityType.SNOWBALL) {
                event.entity.scheduleRemove(Duration.ofSeconds(5))
            }
        }
    }

    private fun hookFireballEvents(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            val itemStack = event.player.getItemInHand(event.hand)
            if (itemStack.material() == Material.FIRE_CHARGE) {

                if (event.player.gameMode != GameMode.CREATIVE) {
                    event.player.inventory.setItemInHand(event.hand, itemStack.withAmount(itemStack.amount() - 1))
                }

                // Shoot a snowball from the player's position
                val snowball = EntityProjectile(event.player, EntityType.FIREBALL)
                snowball.setInstance(event.instance, getEyePos(event.player))
                snowball.shoot(getLaunchPos(event.player), 3.0, 1.0)
                snowball.scheduleRemove(Duration.ofSeconds(30))
            }
        }
        eventNode.addListener(ProjectileCollideWithEntityEvent::class.java) { event ->
            if (event.entity.entityType == EntityType.FIREBALL) {
                explodeFireball(event.entity)
            }
        }
        eventNode.addListener(ProjectileCollideWithBlockEvent::class.java) { event ->
            if (event.entity.entityType == EntityType.FIREBALL) {
                explodeFireball(event.entity)
            }
        }
    }

    private fun explodeFireball(projectile: Entity) {
        projectile as EntityProjectile
        projectile.remove()
        val pos = projectile.position

        if (projectile.instance?.explosionSupplier == null) {
            projectile.instance?.setExplosionSupplier { centerX, centerY, centerZ, strength, _ ->
                object : Explosion(centerX, centerY, centerZ, strength) {

                    override fun prepare(instance: Instance?): List<Point> = MapUtils.getAllInBox(
                        Pos(centerX.toDouble() - strength, centerY.toDouble() - strength, centerZ.toDouble() - strength),
                        Pos(centerX.toDouble() + strength, centerY.toDouble() + strength, centerZ.toDouble() + strength)
                    ).filter { pos ->
                        val distance = pos.distanceSquared(centerX.toDouble(), centerY.toDouble(), centerZ.toDouble())
                        val resistance = instance!!.getBlock(pos).registry().explosionResistance()
                        Random.nextDouble(0.0, strength * 1.5 - resistance / 2.0) > distance
                    }
                }
            }
        }

        val radius = 8.0f
        projectile.instance?.explode(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat(), radius)

        val center = Pos(pos.x, pos.y, pos.z)
        projectile.instance!!.entityTracker.nearbyEntities(
            center, 5.0, EntityTracker.Target.ENTITIES
        ) { entity ->
            val distanceSq = entity.position.distanceSquared(center)
            val multiplier = (1.0f / radius) * distanceSq
            OldCombatModule.takeKnockback(-projectile.velocity.x, -projectile.velocity.z, entity, multiplier)
            if (entity is LivingEntity) {
                entity.damage(DamageType.fromProjectile(projectile.shooter, projectile), multiplier.toFloat() * 8.0f)
            }
        }
    }

    private fun hookEggEvents(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            val itemStack = event.player.getItemInHand(event.hand)
            if (itemStack.material() == Material.EGG) {

                if (event.player.gameMode != GameMode.CREATIVE) {
                    event.player.inventory.setItemInHand(event.hand, itemStack.withAmount(itemStack.amount() - 1))
                }

                // Shoot a snowball from the player's position
                val snowball = EntityProjectile(event.player, EntityType.EGG)
                snowball.setInstance(event.instance, getEyePos(event.player))
                snowball.shoot(getLaunchPos(event.player), 3.0, 1.0)
                snowball.scheduleRemove(Duration.ofSeconds(30))
            }
        }
        eventNode.addListener(ProjectileCollideWithEntityEvent::class.java) { event ->
            val target = event.target as? LivingEntity ?: return@addListener
            val projectile = event.entity as EntityProjectile

            if (projectile.entityType != EntityType.EGG) return@addListener

            if ((target as? Player)?.isInvincible() == true) return@addListener

            OldCombatModule.takeKnockback(-projectile.velocity.x, -projectile.velocity.z, target, 0.0)
            target.damage(EGG_DAMAGE_TYPE, 0.0f)
            projectile.remove()
            (target as? Player)?.resetInvincibilityPeriod()
        }
        eventNode.addListener(ProjectileCollideWithBlockEvent::class.java) { event ->
            if (event.entity.entityType == EntityType.EGG) {
                event.entity.remove()
            }
        }
    }

    private fun Player.isInvincible(): Boolean {
        return getTag(OldCombatModule.HURT_RESISTANT_TIME) > getTag(OldCombatModule.MAX_HURT_RESISTANT_TIME) / 2.0f
    }

    private fun Player.resetInvincibilityPeriod() {
        setTag(OldCombatModule.HURT_RESISTANT_TIME, getTag(OldCombatModule.MAX_HURT_RESISTANT_TIME))
    }

    private fun getLaunchPos(player: Player): Pos {
        return getEyePos(player)
            .add(player.position.direction())
            .sub(0.0, 0.2, 0.0)
    }

    private fun takeOne(player: Player, material: Material): Boolean {
        val stack = player.inventory.itemStacks.firstOrNull { it.material() == material }?.withAmount(1)
        if (stack != null) {
            player.inventory.takeItemStack(stack, TransactionOption.ALL)
            return true
        } else {
            player.inventory.update()
            return false
        }
    }
}
