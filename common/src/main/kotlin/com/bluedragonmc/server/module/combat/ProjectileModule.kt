package com.bluedragonmc.server.module.combat

import ca.atlasengine.projectiles.entities.ArrowProjectile
import ca.atlasengine.projectiles.entities.FireballProjectile
import ca.atlasengine.projectiles.entities.ThrownItemProjectile
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.ProjectileBreakBlockEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.vanilla.ItemDropModule
import com.bluedragonmc.server.utils.CoordinateUtils
import net.kyori.adventure.sound.Sound
import net.minestom.server.ServerFlag
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.*
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.entity.metadata.projectile.ArrowMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventDispatcher
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent
import net.minestom.server.event.entity.projectile.ProjectileCollideWithEntityEvent
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent
import net.minestom.server.event.item.PlayerBeginItemUseEvent
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Explosion
import net.minestom.server.instance.Instance
import net.minestom.server.inventory.TransactionOption
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import java.time.Duration
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

class ProjectileModule : GameModule() {

    class CustomArrowProjectile(val shooter: Entity?, entityType: EntityType) : ArrowProjectile(entityType, shooter)
    class CustomItemProjectile(val shooter: Entity?, entityType: EntityType) : ThrownItemProjectile(entityType, shooter)
    class CustomFireballProjectile(val shooter: Entity?, entityType: EntityType) :
        FireballProjectile(entityType, shooter)

    private lateinit var parent: Game

    companion object {
        private val ARROW_POWER_TAG = Tag.Integer("entity_arrow_power").defaultValue(0) // the power enchantment level
        private val ARROW_PUNCH_TAG = Tag.Integer("entity_arrow_punch").defaultValue(0) // the punch enchantment level
        private val PEARL_OWNER_TAG = Tag.UUID("ender_pearl_owner")
        private val LAST_PEARL_THROWN_TAG = Tag.Long("last_ender_pearl_thrown").defaultValue(0)
        private val LAST_PROJECTILE_THROW_TAG = Tag.Long("last_projectile_throw_time").defaultValue(0)
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent

        hookBowEvents(eventNode)
        hookSnowballEvents(eventNode)
        hookEggEvents(eventNode)
        hookFireballEvents(eventNode)
        hookEnderPearlEvents(eventNode)

        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.removeTag(LAST_PROJECTILE_THROW_TAG)
        }
    }

    /**
     * Hook events that allow players to shoot bows and be shot by arrows.
     * Implementation originally created for Minestom Arena:
     * https://github.com/Minestom/Arena/blob/master/src/main/java/net/minestom/arena/feature/BowFeature.java
     */
    private fun hookBowEvents(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerBeginItemUseEvent::class.java) { event ->
            // Don't allow charging the bow if you have no arrows
            if (!event.player.inventory.itemStacks.any { it.material() == Material.ARROW }) {
                event.isCancelled = true
                return@addListener
            }
        }
        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.BOW) return@addListener

            val secondsCharged: Double = event.useDuration.toDouble() / ServerFlag.SERVER_TICKS_PER_SECOND
            val power = ((secondsCharged * secondsCharged + 2 * secondsCharged) / 2.0).coerceIn(0.0, 1.0)

            if (power > 0.2) {
                if (event.player.gameMode != GameMode.CREATIVE) {
                    if (!takeArrow(event.player)) return@addListener
                }
                val projectile = CustomArrowProjectile(event.player, EntityType.ARROW)
                if (power > 0.9) projectile.isCritical = true
                projectile.scheduleRemove(Duration.ofSeconds(30))
                val eyePos = getEyePos(event.player)

                projectile.setInstance(event.player.instance!!, eyePos)
                projectile.shoot(getLaunchPos(event.player), power * 3.0, 1.0)
                event.player.instance?.playSound(
                    Sound.sound(
                        SoundEvent.ENTITY_ARROW_SHOOT,
                        Sound.Source.MASTER,
                        1.0f,
                        1.0f
                    ), event.player.position
                )
                val enchantments = event.itemStack.get(DataComponents.ENCHANTMENTS)
                projectile.setTag(ARROW_PUNCH_TAG, enchantments?.level(Enchantment.PUNCH))
                projectile.setTag(ARROW_POWER_TAG, enchantments?.level(Enchantment.POWER))
                if (enchantments?.has(Enchantment.FLAME) == true) {
                    projectile.entityMeta.isOnFire = true
                }
            }
        }
        eventNode.addListener(ProjectileCollideWithEntityEvent::class.java) { event ->
            val target = event.target as? LivingEntity ?: return@addListener
            val projectile = event.entity as? CustomArrowProjectile ?: return@addListener

            val arrowMeta = projectile.entityMeta as? ArrowMeta ?: return@addListener
            val shooter = projectile.shooter

            if (target is Player) {
                // Prevent players in creative and spectator modes from being hit by arrows
                if (target.gameMode == GameMode.CREATIVE || target.gameMode == GameMode.SPECTATOR) return@addListener
                if (target.isInvincible()) return@addListener
            }

            if (shooter is Player) {
                val event = OldCombatModule.PlayerAttackEvent(event.instance, shooter, target)
                EventDispatcher.call(event)
                if (event.isCancelled) return@addListener
            }

            OldCombatModule.takeKnockback(
                -projectile.velocity.x,
                -projectile.velocity.z,
                target,
                projectile.getTag(ARROW_PUNCH_TAG).toDouble()
            )

            // https://minecraft.wiki/w/Arrow#Damage

            // Damage tag = 2. If the bow had the power enchant, add 0.5 + another 0.5 for every level of power.
            var damageTag = 2.0
            if (projectile.getTag(ARROW_POWER_TAG) > 0) {
                damageTag += projectile.getTag(ARROW_POWER_TAG) * 0.5 + 0.5
            }

            // Base damage = speed (blocks/tick) * damage tag
            val baseDamage =
                ceil(projectile.velocity.length() / ServerFlag.SERVER_TICKS_PER_SECOND * damageTag).toFloat()
                    .coerceAtLeast(0.0f)

            // If the arrow is critical (fully charged from a bow), increase its damage by up to (damage / 2) + 2
            val damage = if (arrowMeta.isCritical) baseDamage + Random.nextInt((baseDamage / 2.0 + 2.0).toInt())
            else baseDamage

            val reducedDamage = EnumArmorToughness.ArmorToughness.getReducedDamage(damage.toDouble(), target)

            if (shooter is Player && target is Player && !arrowMeta.isSilent) {
                shooter.sendPacket(ChangeGameStatePacket(ChangeGameStatePacket.Reason.ARROW_HIT_PLAYER, 0.0f))
            }

            target.damage(
                Damage(
                    DamageType.ARROW,
                    projectile,
                    projectile.shooter ?: projectile,
                    null,
                    reducedDamage.toFloat()
                )
            )
            target.arrowCount++
            projectile.remove()
            (target as? Player)?.resetInvincibilityPeriod()

            if (projectile.isOnFire) {
                // If the arrow is on fire, it sets the target entity on fire for 5 seconds
                target.fireTicks = ServerFlag.SERVER_TICKS_PER_SECOND * 5
            }
        }
        eventNode.addListener(ProjectileCollideWithBlockEvent::class.java) { event ->
            val projectile = event.entity as? CustomArrowProjectile ?: return@addListener
            projectile.isCritical = false
            projectile.scheduleRemove(Duration.ofSeconds(60))
        }
    }

    private fun getEyePos(player: Player): Pos {
        return player.position.add(0.0, player.eyeHeight, 0.0)
    }

    private fun hookSnowballEvents(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            val itemStack = event.player.getItemInHand(event.hand)
            if (itemStack.material() == Material.SNOWBALL) {
                if (event.player.isOnCooldown()) return@addListener

                if (event.player.gameMode != GameMode.CREATIVE) {
                    event.player.setItemInHand(event.hand, itemStack.withAmount(itemStack.amount() - 1))
                }

                // Shoot a snowball from the player's position
                val snowball = CustomItemProjectile(event.player, EntityType.SNOWBALL)
                snowball.setInstance(event.instance, getEyePos(event.player))
                snowball.shoot(getLaunchPos(event.player), 3.0, 1.0)
                event.player.instance?.playSound(
                    Sound.sound(
                        SoundEvent.ENTITY_SNOWBALL_THROW,
                        Sound.Source.MASTER,
                        1.0f,
                        0.5f
                    ), event.player.position
                )
                snowball.scheduleRemove(Duration.ofSeconds(30))
            }
        }
        eventNode.addListener(ProjectileCollideWithEntityEvent::class.java) { event ->
            val target = event.target as? LivingEntity ?: return@addListener
            val projectile = event.entity as? CustomItemProjectile ?: return@addListener

            if (projectile.entityType != EntityType.SNOWBALL) return@addListener

            if ((target as? Player)?.isInvincible() == true) return@addListener

            if (projectile.shooter is Player) {
                val event = OldCombatModule.PlayerAttackEvent(event.instance, projectile.shooter, target)
                EventDispatcher.call(event)
                if (event.isCancelled) return@addListener
            }

            OldCombatModule.takeKnockback(-projectile.velocity.x, -projectile.velocity.z, target, 0.0)
            target.damage(Damage(DamageType.THROWN, projectile, projectile.shooter, projectile.shooter?.position, 0.0f))
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
            shootFireball(event.player, event.hand, event.instance)
        }
        eventNode.addListener(PlayerUseItemOnBlockEvent::class.java) { event ->
            shootFireball(event.player, event.hand, event.instance)
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

    private fun shootFireball(player: Player, hand: PlayerHand, instance: Instance) {
        val itemStack = player.getItemInHand(hand)
        if (itemStack.material() == Material.FIRE_CHARGE) {
            if (player.isOnCooldown()) return

            if (player.gameMode != GameMode.CREATIVE) {
                player.setItemInHand(hand, itemStack.withAmount(itemStack.amount() - 1))
            }

            // Shoot a fireball from the player's position
            val fireball = CustomFireballProjectile(player, EntityType.FIREBALL)
            fireball.setInstance(instance, getEyePos(player))
            fireball.shoot(getLaunchPos(player), 3.0, 1.0)
            player.instance?.playSound(
                Sound.sound(SoundEvent.ITEM_FIRECHARGE_USE, Sound.Source.MASTER, 1.0f, 1.0f),
                player.position
            )
            fireball.scheduleRemove(Duration.ofSeconds(30))
        }
    }

    private fun explodeFireball(projectile: Entity) {
        if (projectile !is CustomFireballProjectile) return
        val pos = projectile.position

        val radius = 8.0f
        val explosion = createExplosion(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat(), radius, projectile)
        explosion.apply(projectile.instance!!)

        val center = Pos(pos.x, pos.y, pos.z)
        projectile.instance!!.entityTracker.nearbyEntities(
            center, radius.toDouble(), EntityTracker.Target.ENTITIES
        ) { entity ->
            val mult = radius - entity.position.distance(center)
            if (projectile.shooter is Player) {
                val event = OldCombatModule.PlayerAttackEvent(projectile.instance, projectile.shooter, entity)
                EventDispatcher.call(event)
                if (event.isCancelled) return@nearbyEntities
            }
            OldCombatModule.takeKnockback(
                (entity.position.x - projectile.position.x) * projectile.velocity.x,
                (entity.position.z - projectile.position.z) * projectile.velocity.z,
                entity,
                mult
            )
            if (entity is LivingEntity) {
                entity.damage(
                    Damage(
                        DamageType.FIREBALL,
                        projectile,
                        projectile.shooter ?: projectile,
                        null,
                        mult.toFloat()
                    )
                )
            }
        }

        projectile.remove()
    }

    private fun createExplosion(
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        strength: Float,
        projectile: CustomFireballProjectile,
    ) = object : Explosion(centerX, centerY, centerZ, strength) {

        override fun prepare(instance: Instance): List<Point> {
            val dropItems = parent.getModuleOrNull<ItemDropModule>()?.dropBlocksOnBreak == true
            val positions = CoordinateUtils.getAllInBox(
                Pos(centerX.toDouble() - strength, centerY.toDouble() - strength, centerZ.toDouble() - strength),
                Pos(centerX.toDouble() + strength, centerY.toDouble() + strength, centerZ.toDouble() + strength)
            ).filter { pos ->
                // Check if the block should be destroyed based on the radius
                val distance = pos.distanceSquared(centerX.toDouble(), centerY.toDouble(), centerZ.toDouble())
                val resistance = instance.getBlock(pos).registry().explosionResistance()
                val maxStrength = strength * 1.5 - resistance / 2.0
                if (maxStrength <= 0 || Random.nextDouble(0.0, maxStrength) < distance)
                    return@filter false

                // Check if the shooter is allowed to break the block
                // (seeing if a PlayerBlockBreakEvent would be cancelled)
                val player = projectile.shooter as? Player ?: return@filter false
                val block = instance.getBlock(pos)
                val event = ProjectileBreakBlockEvent(parent, player, block, pos)
                parent.callEvent(event)

                if (dropItems && !event.isCancelled) {
                    val material = block.registry().material()
                    if (material != null)
                        parent.getModule<ItemDropModule>().dropItem(ItemStack.of(material), instance, pos)
                }
                return@filter !event.isCancelled
            }

            return positions
        }
    }

    private fun hookEggEvents(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            throwEgg(event.player, event.hand, event.instance)
        }
        eventNode.addListener(PlayerUseItemOnBlockEvent::class.java) { event ->
            throwEgg(event.player, event.hand, event.instance)
        }
        eventNode.addListener(ProjectileCollideWithEntityEvent::class.java) { event ->
            val target = event.target as? LivingEntity ?: return@addListener
            val projectile = event.entity as? CustomItemProjectile ?: return@addListener

            if (projectile.entityType != EntityType.EGG) return@addListener

            if ((target as? Player)?.isInvincible() == true) return@addListener

            if (projectile.shooter is Player) {
                val event = OldCombatModule.PlayerAttackEvent(event.instance, projectile.shooter, target)
                EventDispatcher.call(event)
                if (event.isCancelled) return@addListener
            }

            OldCombatModule.takeKnockback(-projectile.velocity.x, -projectile.velocity.z, target, 0.0)
            target.damage(Damage(DamageType.THROWN, projectile, projectile.shooter, null, 0.0f))
            projectile.remove()
            (target as? Player)?.resetInvincibilityPeriod()
        }
        eventNode.addListener(ProjectileCollideWithBlockEvent::class.java) { event ->
            if (event.entity.entityType == EntityType.EGG) {
                event.entity.remove()
            }
        }
    }

    private fun throwEgg(player: Player, hand: PlayerHand, instance: Instance) {
        val itemStack = player.getItemInHand(hand)
        if (itemStack.material() == Material.EGG) {
            if (player.isOnCooldown()) return

            if (player.gameMode != GameMode.CREATIVE) {
                player.setItemInHand(hand, itemStack.withAmount(itemStack.amount() - 1))
            }

            // Shoot an egg from the player's position
            val egg = CustomItemProjectile(player, EntityType.EGG)
            egg.setInstance(instance, getEyePos(player))
            egg.shoot(getLaunchPos(player), 3.0, 1.0)
            instance.playSound(
                Sound.sound(SoundEvent.ENTITY_EGG_THROW, Sound.Source.MASTER, 1.0f, 0.5f),
                player.position
            )
            egg.scheduleRemove(Duration.ofSeconds(30))
        }
    }

    private fun hookEnderPearlEvents(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerUseItemOnBlockEvent::class.java) { event ->
            if (event.player.getItemInHand(event.hand).material() == Material.ENDER_PEARL) {
                // Don't throw ender pearls if the player right-clicked a block with them
                event.player.inventory.update()
            }
        }

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            val itemStack = event.player.getItemInHand(event.hand)
            if (itemStack.material() == Material.ENDER_PEARL) {
                if (event.player.isOnCooldown(
                        LAST_PEARL_THROWN_TAG,
                        ServerFlag.SERVER_TICKS_PER_SECOND.toLong() // Ender pearls have a cooldown of 1 second
                    )
                ) return@addListener

                if (event.player.gameMode != GameMode.CREATIVE) {
                    event.player.setItemInHand(event.hand, itemStack.withAmount(itemStack.amount() - 1))
                }

                val pearl = CustomItemProjectile(event.player, EntityType.ENDER_PEARL)
                pearl.setTag(PEARL_OWNER_TAG, event.player.uuid)
                pearl.setInstance(event.instance, getEyePos(event.player))
                pearl.shoot(getLaunchPos(event.player), 2.5, 1.0)
                event.player.instance?.playSound(
                    Sound.sound(
                        SoundEvent.ENTITY_ENDER_PEARL_THROW,
                        Sound.Source.MASTER,
                        1.0f,
                        0.5f
                    ), event.player.position
                )
                pearl.scheduleRemove(Duration.ofSeconds(30))
            }
        }

        eventNode.addListener(RemoveEntityFromInstanceEvent::class.java) { event ->
            // When a player switches instances, remove all their thrown ender pearls
            if (event.entity !is Player) return@addListener
            val enderPearls = event.instance.entities.filter {
                it.getTag(PEARL_OWNER_TAG) == event.entity.uuid
            }
            enderPearls.forEach { it.remove() }
        }

        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            // When a player dies, remove all their thrown ender pearls
            val enderPearls = event.instance.entities.filter {
                it.getTag(PEARL_OWNER_TAG) == event.entity.uuid
            }
            enderPearls.forEach { it.remove() }
        }

        eventNode.addListener(ProjectileCollideWithBlockEvent::class.java) { event ->
            if (event.entity.entityType == EntityType.ENDER_PEARL) {
                event.entity.instance?.players?.firstOrNull { it.uuid == event.entity.getTag(PEARL_OWNER_TAG) }?.apply {
                    teleport(event.entity.position.add(0.0, 1.0, 0.0))
                    damage(
                        Damage(
                            DamageType.THROWN,
                            event.entity,
                            this,
                            null,
                            5.0f
                        )
                    )
                    event.entity.instance?.playSound(
                        Sound.sound(
                            SoundEvent.ENTITY_ENDERMAN_TELEPORT,
                            Sound.Source.MASTER,
                            1.0f,
                            1.0f
                        ), event.entity.position
                    )
                }
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

    private fun Player.isOnCooldown(tag: Tag<Long> = LAST_PROJECTILE_THROW_TAG, cooldown: Long = 1): Boolean {
        val timeSinceLastThrow = aliveTicks - getTag(tag)
        if (abs(timeSinceLastThrow) <= cooldown) return true
        setTag(tag, aliveTicks)
        return false
    }

    private fun getLaunchPos(player: Player): Pos {
        return getEyePos(player)
            .add(player.position.direction())
            .sub(0.0, 0.2, 0.0)
    }

    private fun takeArrow(player: Player): Boolean {
        val stack = player.inventory.itemStacks.firstOrNull { it.material() == Material.ARROW }?.withAmount(1)

        if (stack == null) {
            player.inventory.update()
            return false
        }

        player.inventory.takeItemStack(stack, TransactionOption.ALL)
        return true
    }
}
