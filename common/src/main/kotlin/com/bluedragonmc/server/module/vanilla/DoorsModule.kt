package com.bluedragonmc.server.module.vanilla

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

/**
 * Adapted from BasicRedstone by TogAr2 under the MIT License
 * Modified to work with the latest minestom-ce
 * https://github.com/TogAr2/BasicRedstone/blob/master/src/main/java/io/github/bloepiloepi/basicredstone/door/Doors.java
 */
class DoorsModule : GameModule() {

    private val woodDoors = listOf(
        Block.ACACIA_DOOR,
        Block.BAMBOO_DOOR,
        Block.BIRCH_DOOR,
        Block.DARK_OAK_DOOR,
        Block.CHERRY_DOOR,
        Block.CRIMSON_DOOR,
        Block.JUNGLE_DOOR,
        Block.MANGROVE_DOOR,
        Block.SPRUCE_DOOR,
        Block.WARPED_DOOR,
        Block.OAK_DOOR,
        Block.COPPER_DOOR,
        Block.EXPOSED_COPPER_DOOR,
        Block.WEATHERED_COPPER_DOOR,
        Block.OXIDIZED_COPPER_DOOR,
        Block.WAXED_COPPER_DOOR,
        Block.WAXED_EXPOSED_COPPER_DOOR,
        Block.WAXED_WEATHERED_COPPER_DOOR,
        Block.WAXED_OXIDIZED_COPPER,
    )

    private fun isDoor(block: Block) = woodDoors.any { it.compare(block, Block.Comparator.ID) }

    private fun setOpen(instance: Instance, position: Point, open: Boolean, playEffect: Boolean, source: Player?) {
        val block: Block = instance.getBlock(position)

        // Modify the half that the player clicked
        instance.setBlock(
            position, block.withProperty("open", open.toString())
        )

        // Modify the other half
        val half = block.getProperty("half")
        val otherHalfPos = if (half == "upper") {
            position.sub(0.0, 1.0, 0.0)
        } else {
            position.add(0.0, 1.0, 0.0)
        }

        instance.setBlock(
            otherHalfPos, instance.getBlock(otherHalfPos).withProperty("open", open.toString())
        )

        // TODO Play effect only if state changed
        // MC 1.21.4 replaced EffectPacket with WorldEventPacket, but opening a door is not a world event...
//        val shouldPlayEffect = playEffect && (block.getProperty("open").equals("true")) != open
//
//        if (shouldPlayEffect) {
//            // Play an effect to nearby players
//            val effect = if (open) Effects.WOODEN_DOOR_OPENED else Effects.WOODEN_DOOR_CLOSED
//
//            val audience = mutableListOf<Player>()
//            instance.entityTracker.nearbyEntities(position, 64.0, EntityTracker.Target.PLAYERS) { player ->
//                audience.add(
//                    player
//                )
//            }
//            PacketSendingUtils.sendGroupedPacket(
//                audience, EffectPacket(effect.id, position, 0, false)
//            ) { player -> player != source }
//        }
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.player.isSneaking || !isDoor(event.block)) return@addListener

            event.isBlockingItemUse = true

            val isOpen = event.block.getProperty("open").equals("true")

            setOpen(
                event.instance, event.blockPosition, open = !isOpen, playEffect = true, source = event.player
            )
        }
    }
}