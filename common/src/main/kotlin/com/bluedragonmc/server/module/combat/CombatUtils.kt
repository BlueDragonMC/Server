package com.bluedragonmc.server.module.combat

import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.enchant.Enchantment
import kotlin.random.Random

object CombatUtils {
    fun damageItemStack(itemStack: ItemStack, amount: Int): ItemStack {
        val unbreakingLevel = itemStack.get(ItemComponent.ENCHANTMENTS)?.enchantments?.get(Enchantment.UNBREAKING) ?: 0
        val processedAmount = (0 until amount).count { !shouldRestoreDurability(itemStack, unbreakingLevel) }
        // todo - if the damage increases beyond the max damage (durability), do we need to manually delete the item?
        return itemStack.with(ItemComponent.DAMAGE, (itemStack.get(ItemComponent.DAMAGE) ?: 0) + processedAmount)
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

    fun shouldCauseThorns(level: Int): Boolean = if (level <= 0) false else Random.nextFloat() < 0.15 * level

    fun getThornsDamage(level: Int): Int = if (level > 10) 10 - level else 1 + Random.nextInt(4)

    fun getDamageModifier(enchants: Map<Enchantment, Int>, targetEntity: Entity): Float =
        if (enchants.containsKey(Enchantment.SHARPNESS)) {
            enchants[Enchantment.SHARPNESS]!! * 1.25f
        } else if (enchants.containsKey(Enchantment.SMITE) && isUndead(targetEntity)) {
            enchants[Enchantment.SMITE]!! * 2.5f
        } else if (enchants.containsKey(Enchantment.BANE_OF_ARTHROPODS) && isArthropod(targetEntity)) {
            enchants[Enchantment.BANE_OF_ARTHROPODS]!! * 2.5f
        } else 0.0f

    fun getArrowDamageModifier(enchants: Map<Enchantment, Int>, targetEntity: Entity): Float =
        if (enchants.containsKey(Enchantment.POWER))
            0.5f + enchants[Enchantment.POWER]!! * 0.5f
        else 0.0f


    private val UNDEAD_MOBS = setOf(
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
    )

    private fun isUndead(entity: Entity) = UNDEAD_MOBS.contains(entity.entityType)

    private fun isArthropod(entity: Entity) =
        entity.entityType == EntityType.SPIDER || entity.entityType == EntityType.CAVE_SPIDER || entity.entityType == EntityType.ENDERMITE || entity.entityType == EntityType.SILVERFISH

}