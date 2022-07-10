package com.bluedragonmc.server.module.combat

import com.bluedragonmc.server.module.combat.EnumItemDamage.ItemDamage.materialMap
import net.minestom.server.item.Material

enum class EnumItemDamage(material: Material, private val attackDamage: Float) {
    WOODEN_SWORD(Material.WOODEN_SWORD, 4f),
    GOLD_SWORD(Material.GOLDEN_SWORD, 4f),
    STONE_SWORD(Material.STONE_SWORD, 5f),
    IRON_SWORD(Material.IRON_SWORD, 6f),
    DIAMOND_SWORD(Material.DIAMOND_SWORD, 7f),
    NETHERITE_SWORD(Material.NETHERITE_SWORD, 8f),

    WOODEN_SHOVEL(Material.WOODEN_SHOVEL, 2.5f),
    GOLDEN_SHOVEL(Material.GOLDEN_SHOVEL, 2.5f),
    STONE_SHOVEL(Material.STONE_SHOVEL, 3.5f),
    IRON_SHOVEL(Material.IRON_SHOVEL, 4.5f),
    DIAMOND_SHOVEL(Material.DIAMOND_SHOVEL, 5.5f),
    NETHERITE_SHOVEL(Material.NETHERITE_SHOVEL, 6.5f),

    WOODEN_PICKAXE(Material.WOODEN_PICKAXE, 2f),
    GOLDEN_PICKAXE(Material.GOLDEN_PICKAXE, 2f),
    STONE_PICKAXE(Material.STONE_PICKAXE, 3f),
    IRON_PICKAXE(Material.IRON_PICKAXE, 4f),
    DIAMOND_PICKAXE(Material.DIAMOND_PICKAXE, 5f),
    NETHERITE_PICKAXE(Material.NETHERITE_PICKAXE, 6f),

    WOODEN_AXE(Material.WOODEN_AXE, 7f),
    GOLDEN_AXE(Material.GOLDEN_AXE, 7f),
    STONE_AXE(Material.STONE_AXE, 9f),
    IRON_AXE(Material.IRON_AXE, 9f),
    DIAMOND_AXE(Material.DIAMOND_AXE, 9f),
    NETHERITE_AXE(Material.NETHERITE_AXE, 10f),

    WOODEN_HOE(Material.WOODEN_HOE, 1f),
    GOLDEN_HOE(Material.GOLDEN_HOE, 1f),
    STONE_HOE(Material.STONE_HOE, 1f),
    IRON_HOE(Material.IRON_HOE, 1f),
    DIAMOND_HOE(Material.DIAMOND_HOE, 1f),
    NETHERITE_HOE(Material.NETHERITE_HOE, 1f);

    init {
        materialMap[material] = this
    }

    object ItemDamage {
        val materialMap = mutableMapOf<Material, EnumItemDamage>()

        fun getAttackDamage(material: Material) = materialMap[material]?.attackDamage ?: 1f
    }
}