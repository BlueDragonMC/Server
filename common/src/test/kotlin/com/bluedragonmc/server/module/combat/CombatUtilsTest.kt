package com.bluedragonmc.server.module.combat

import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.registry.RegistryKey
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CombatUtilsTest {

    private fun damageModifier(enchantment: RegistryKey<Enchantment>, target: EntityType): Float =
        CombatUtils.getDamageModifier(mapOf(enchantment to 1), Entity(target))

    /**
     * Smite should apply to entities in Minestom's `minecraft:undead` tag.
     */
    @Test
    fun `Smite applies to undead entities`() {
        assertEquals(2.5f, damageModifier(Enchantment.SMITE, EntityType.ZOMBIE))
        assertEquals(2.5f, damageModifier(Enchantment.SMITE, EntityType.SKELETON))
        assertEquals(0.0f, damageModifier(Enchantment.SMITE, EntityType.COW))
    }

    /**
     * Bane of Arthropods should apply to entities in Minestom's `minecraft:arthropod` tag.
     */
    @Test
    fun `Bane of arthropods applies to arthropod entities`() {
        assertEquals(2.5f, damageModifier(Enchantment.BANE_OF_ARTHROPODS, EntityType.SPIDER))
        assertEquals(2.5f, damageModifier(Enchantment.BANE_OF_ARTHROPODS, EntityType.BEE))
        assertEquals(0.0f, damageModifier(Enchantment.BANE_OF_ARTHROPODS, EntityType.COW))
    }
}
