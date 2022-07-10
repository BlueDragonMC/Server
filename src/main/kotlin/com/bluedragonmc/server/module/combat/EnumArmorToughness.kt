package com.bluedragonmc.server.module.combat

import com.bluedragonmc.server.module.combat.EnumArmorToughness.ArmorToughness.armorDataMap
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlin.math.max
import kotlin.math.min

enum class EnumArmorToughness(val armorToughness: Int, val defensePoints: Int, vararg materials: Material) {

    TURTLE_SHELL(0, 2, Material.TURTLE_HELMET),
    LEATHER_HELMET(0, 1, Material.LEATHER_HELMET),
    GOLDEN_HELMET(0, 2, Material.GOLDEN_HELMET),
    CHAIN_HELMET(0, 2, Material.CHAINMAIL_HELMET),
    IRON_HELMET(0, 2, Material.IRON_HELMET),
    DIAMOND_HELMET(2, 3, Material.DIAMOND_HELMET),
    NETHERITE_HELMET(3, 3, Material.NETHERITE_HELMET),

    LEATHER_CHESTPLATE(0, 3, Material.LEATHER_CHESTPLATE),
    GOLDEN_CHESTPLATE(0, 5, Material.GOLDEN_CHESTPLATE),
    CHAINMAIL_CHESTPLATE(0, 5, Material.CHAINMAIL_CHESTPLATE),
    IRON_CHESTPLATE(0, 6, Material.IRON_CHESTPLATE),
    DIAMOND_CHESTPLATE(2, 8, Material.DIAMOND_CHESTPLATE),
    NETHERITE_CHESTPLATE(3, 8, Material.NETHERITE_CHESTPLATE),

    LEATHER_LEGGINGS(0, 2, Material.LEATHER_LEGGINGS),
    GOLDEN_LEGGINGS(0, 3, Material.GOLDEN_LEGGINGS),
    CHAINMAIL_LEGGINGS(0, 4, Material.CHAINMAIL_LEGGINGS),
    IRON_LEGGINGS(0, 5, Material.IRON_LEGGINGS),
    DIAMOND_LEGGINGS(2, 6, Material.DIAMOND_LEGGINGS),
    NETHERITE_LEGGINGS(3, 6, Material.NETHERITE_LEGGINGS),

    LEATHER_BOOTS(0, 1, Material.LEATHER_BOOTS),
    GOLDEN_BOOTS(0, 1, Material.GOLDEN_BOOTS),
    CHAINMAIL_BOOTS(0, 1, Material.CHAINMAIL_BOOTS),
    IRON_BOOTS(0, 2, Material.IRON_BOOTS),
    DIAMOND_BOOTS(2, 3, Material.DIAMOND_BOOTS),
    NETHERITE_BOOTS(3, 3, Material.NETHERITE_BOOTS);

    init {
        materials.forEach {
            armorDataMap[it] = armorToughness to defensePoints
        }
    }

    object ArmorToughness {

        val armorDataMap = mutableMapOf<Material, Pair<Int, Int>>()

        private fun Player.getArmor() = listOf(helmet, chestplate, leggings, boots)
        private fun ItemStack.getArmorToughness() = armorDataMap[this.material()]?.first ?: 0
        private fun ItemStack.getDefensePoints() = armorDataMap[this.material()]?.second ?: 0

        fun getReducedDamage(incomingDamage: Float, target: Player): Float {
            val armor = target.getArmor()
            val armorDefense = armor.sumOf { it.getDefensePoints() }
            val armorToughness = armor.sumOf { it.getArmorToughness() }
            return getReducedDamage(incomingDamage, armorDefense, armorToughness)
        }

        /**
         * https://minecraft.fandom.com/wiki/Armor#Defense_points
         */
        private fun getReducedDamage(incomingDamage: Float, armorDefense: Int, armorToughness: Int): Float {
            return incomingDamage * (1f - min(
                20f, max(armorDefense / 5f, armorDefense - incomingDamage / (2f + armorToughness / 4f))
            ) / 25f)
        }
    }
}