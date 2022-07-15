package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.EnumArmorToughness.ArmorToughness.getArmor
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Enchantment
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag

object FallDamageModule : GameModule() {

    private val FALL_START_TAG = Tag.Double("fall_start")

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val block = event.instance.getBlock(event.player.position)

            if (block.compare(Block.WATER) || block.compare(Block.COBWEB)) {
                // Water resets fall distance
                event.player.removeTag(FALL_START_TAG)
            } else if (block.compare(Block.LAVA) && event.player.hasTag(FALL_START_TAG)) {
                // Lava decreases fall distance by half each tick
                event.player.setTag(FALL_START_TAG, event.player.getTag(FALL_START_TAG) / 2.0)
            }

            if (!event.isOnGround) {
                // If the player does not have the tag, and they are falling, add the fall start tag.
                if (!event.player.hasTag(FALL_START_TAG) && event.player.velocity.y < 0.0) {
                    event.player.setTag(FALL_START_TAG, event.newPosition.y)
                }
            } else {
                // When the player touches the ground, compare their fall start to their current y-position.
                if (event.player.hasTag(FALL_START_TAG)) {
                    val fallStart = event.player.getTag(FALL_START_TAG)
                    event.player.removeTag(FALL_START_TAG)
                    val fallDamage = getReducedDamage(
                        event.player, fallStart - event.newPosition.y - 3 - getJumpBoostLevel(event.player)
                    )
                    if (fallDamage > 0.0) {
                        event.player.damage(DamageType.GRAVITY, fallDamage.toFloat())
                    }
                }
            }
        }
    }

    private fun getJumpBoostLevel(player: Player) =
        player.activeEffects.filter { it.potion.effect == PotionEffect.JUMP_BOOST }
            .sumOf { it.potion.amplifier.toInt() }

    private fun getReducedDamage(player: Player, originalDamage: Double): Double {
        val blockBelow = player.instance!!.getBlock(player.position.sub(0.0, 0.50, 0.0))
        val blockBelowReduction = when {
            // Honey blocks and hay bales reduce fall damage by 20%
            blockBelow.compare(Block.HAY_BLOCK) || blockBelow.compare(Block.HONEY_BLOCK) -> 0.2
            // Beds reduce fall damage by 50%
            blockBelow.compare(Block.RED_BED, Block.Comparator.ID) -> 0.5
            // Sweet berry bushes and cobwebs negate all fall damage
            blockBelow.compare(Block.SWEET_BERRY_BUSH) || blockBelow.compare(Block.COBWEB) -> 1.0
            else -> 0.0
        }
        // Feather falling reduces fall damage by 12% per level
        val featherFallingLevel = player.boots.meta().enchantmentMap[Enchantment.FEATHER_FALLING] ?: 0
        // Protection reduces damage by 4% per level
        val protLevel = player.getArmor().sumOf { it.meta().enchantmentMap[Enchantment.PROTECTION]?.toInt() ?: 0 }
        val protectionPercentage = ((0.04 * protLevel) + (0.12 * featherFallingLevel)).coerceAtMost(0.8)
        return originalDamage * (1.0 - protectionPercentage) * (1.0 - blockBelowReduction)
    }
}