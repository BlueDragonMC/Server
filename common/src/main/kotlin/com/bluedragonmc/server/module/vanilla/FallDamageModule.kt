package com.bluedragonmc.server.module.vanilla

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.EnumArmorToughness.ArmorToughness.getArmor
import net.minestom.server.collision.BoundingBox
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag
import kotlin.math.floor

class FallDamageModule : GameModule() {

    companion object {
        private val FALL_START_TAG = Tag.Double("fall_start")
        private val LAST_Y_TAG = Tag.Double("last_y")

        private fun getJumpBoostLevel(player: Player) =
            player.activeEffects.filter { it.potion.effect == PotionEffect.JUMP_BOOST }
                .sumOf { it.potion.amplifier.toInt() }

        private fun getReducedDamage(player: Player, originalDamage: Double): Double {
            val blockBelow: Block = getBlockTypeBelow(player)
            val blockBelowReduction = when {
                // Honey blocks and hay bales reduce fall damage by 20%
                blockBelow.compare(Block.HAY_BLOCK) || blockBelow.compare(Block.HONEY_BLOCK) -> 0.2
                // Beds reduce fall damage by 50%
                blockBelow.compare(Block.RED_BED, Block.Comparator.ID) -> 0.5
                // Sweet berry bushes and cobwebs negate all fall damage
                blockBelow.compare(Block.SWEET_BERRY_BUSH) || blockBelow.compare(Block.COBWEB) || (blockBelow.compare(
                    Block.SLIME_BLOCK
                )) -> 1.0

                else -> 0.0
            }
            // Feather falling reduces fall damage by 12% per level
            val featherFallingLevel =
                player.boots.get(ItemComponent.ENCHANTMENTS)?.enchantments?.get(Enchantment.FEATHER_FALLING) ?: 0
            // Protection reduces damage by 4% per level
            val protLevel = player.getArmor()
                .sumOf { it.get(ItemComponent.ENCHANTMENTS)?.enchantments?.get(Enchantment.PROTECTION) ?: 0 }
            val protectionPercentage = ((0.04 * protLevel) + (0.12 * featherFallingLevel)).coerceAtMost(0.8)
            return originalDamage * (1.0 - protectionPercentage) * (1.0 - blockBelowReduction)
        }

        private fun getBlockTypeBelow(player: Player): Block {
            var block = player.instance.getBlock(player.position.sub(0.0, 0.2, 0.0))
            if (block.compare(Block.AIR, Block.Comparator.ID)) {
                // If the block directly below the player is air, then they're probably standing on a different adjacent block
                val supportingBlock = findSupportingBlock(player)
                if (supportingBlock != null) {
                    block = player.instance.getBlock(supportingBlock)
                }
            }

            return block
        }

        private fun findSupportingBlock(player: Player): Pos? {
            // Scan all nearby blocks that the player could be intersecting with.
            // Then, from the subset of blocks that the player collides with,
            // find the closest one to the player (using the center point of the bottom of the player's bounding box and the center of the block's bounding box).
            val minX = floor(player.position.x + player.boundingBox.minX() - Vec.EPSILON).toInt() - 1
            val maxX = floor(player.position.x + player.boundingBox.maxX() + Vec.EPSILON).toInt() + 1
            val minY = floor(player.position.y + player.boundingBox.minY() - 2 * Vec.EPSILON).toInt() - 1
            val maxY = floor(player.position.y + player.boundingBox.maxY() + Vec.EPSILON).toInt() + 1
            val minZ = floor(player.position.z + player.boundingBox.minZ() - Vec.EPSILON).toInt() - 1
            val maxZ = floor(player.position.z + player.boundingBox.maxZ() + Vec.EPSILON).toInt() + 1

            val expandedBoundingBox = BoundingBox(player.boundingBox.relativeStart.sub(0.0, 0.2, 0.0), player.boundingBox.relativeEnd)

            var minDistance = Double.MAX_VALUE
            var supportingBlockPos: Pos? = null

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        // Skip blocks on the corners of the bounding box
                        var touchedEdges = 0
                        if (x == minX || x == maxX) touchedEdges++
                        if (y == minY || y == maxY) touchedEdges++
                        if (z == minZ || z == maxZ) touchedEdges++
                        if (touchedEdges == 3) continue

                        val blockPos = Pos(x.toDouble(), y.toDouble(), z.toDouble())
                        val blockCenter = blockPos.add(0.5, 0.5, 0.5)
                        val blockBoundingBox = player.instance.getBlock(blockPos).registry().collisionShape()

                        val collides = blockBoundingBox.intersectBox(player.position.sub(blockPos), expandedBoundingBox)
                        if (!collides) continue
                        val distance = blockCenter.distanceSquared(player.position)
                        if (distance < minDistance) {
                            minDistance = distance
                            supportingBlockPos = blockPos
                        }
                    }
                }
            }

            return supportingBlockPos
        }
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            val block = event.instance.getBlock(player.position)

            if (block.compare(Block.WATER) || block.compare(Block.COBWEB) || (player as CustomPlayer).isOnLadder() || player.isFlying || player.isAllowFlying) {
                // Water resets fall distance
                player.removeTag(FALL_START_TAG)
            } else if (block.compare(Block.LAVA) && player.hasTag(FALL_START_TAG)) {
                // Lava decreases fall distance by half a block each tick
                player.setTag(FALL_START_TAG, player.getTag(FALL_START_TAG) - 0.5)
            }

            if (getReducedDamage(event.player, 1.0) == 0.0) {
                player.removeTag(FALL_START_TAG)
            }

            if (player.isFlyingWithElytra && player.hasTag(LAST_Y_TAG) && player.getTag(LAST_Y_TAG) - player.position.y <= 0.5) {
                // Fall distance is reset to 1 block when a player is flying level,
                // upwards, or downwards at a rate <= 0.5 blocks per tick.
                player.setTag(FALL_START_TAG, event.player.position.y + 1.0)
            }

            if (event.player.isOnGround) {
                // When the player touches the ground, compare their fall start to their current y-position.
                if (player.hasTag(FALL_START_TAG) && !getBlockTypeBelow(player).compare(Block.AIR)) {
                    val fallStart = player.getTag(FALL_START_TAG)
                    player.removeTag(FALL_START_TAG)
                    val fallDamage = getReducedDamage(
                        player, fallStart - event.player.position.y - 3 - getJumpBoostLevel(player)
                    )
                    if (fallDamage >= 0.5) {
                        player.damage(DamageType.FALL, fallDamage.toFloat())
                    }
                }
            } else {
                // If the player does not have the tag, and they are falling, add the fall start tag.
                if (!player.hasTag(FALL_START_TAG) && player.hasTag(LAST_Y_TAG) &&
                    player.position.y < player.getTag(LAST_Y_TAG)
                ) {
                    player.setTag(FALL_START_TAG, event.player.position.y)
                }
            }

            player.setTag(LAST_Y_TAG, player.position.y)
        }
    }
}