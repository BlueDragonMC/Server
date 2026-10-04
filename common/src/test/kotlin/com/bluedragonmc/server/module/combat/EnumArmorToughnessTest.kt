package com.bluedragonmc.server.module.combat

import com.bluedragonmc.server.module.combat.EnumArmorToughness.ArmorToughness
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.item.Material
import net.minestom.server.registry.Registries
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class EnumArmorToughnessTest {

    /**
     * Fails when a new armor piece is added that isn't listed in [EnumArmorToughness].
     */
    @Test
    fun `Every armor material has a toughness entry`() {
        // Ensure the vanilla registries (and therefore material prototypes) are bound before reading components.
        Registries.vanilla()

        val armorSlots = EquipmentSlot.armors().toSet()
        val armorMaterials = Material.values().filter { material ->
            material.equipmentSlot() in armorSlots &&
                    material.prototype().get(DataComponents.ATTRIBUTE_MODIFIERS)
                        ?.modifiers()
                        ?.any { it.attribute() == Attribute.ARMOR } == true
        }
        assertTrue(armorMaterials.isNotEmpty(), "Expected Minestom to expose armor materials")

        val missing = armorMaterials.filterNot { ArmorToughness.armorDataMap.containsKey(it) }
        assertTrue(
            missing.isEmpty(),
            "No armor values are mapped for the following materials in EnumArmorToughness.kt: " +
                    missing.joinToString { it.key().asString() } +
                    ". Add them to `EnumArmorToughness`."
        )
    }
}
