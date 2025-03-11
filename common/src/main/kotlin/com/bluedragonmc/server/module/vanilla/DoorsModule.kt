package com.bluedragonmc.server.module.vanilla

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import kotlin.random.Random

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
        Block.WAXED_OXIDIZED_COPPER_DOOR,
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

        val shouldPlaySound = playEffect && (block.getProperty("open").equals("true")) != open

        if (shouldPlaySound) {
            val (openSound, closeSound) = (getSounds(block.registry().material() ?: return) ?: return)
            val soundEvent = if (open) openSound else closeSound

            val audience = mutableListOf<Player>()
            instance.entityTracker.nearbyEntities(position, 16.0, EntityTracker.Target.PLAYERS) { player ->
                if (player != source) audience.add(player)
            }
            PacketGroupingAudience.of(audience).playSound(Sound.sound(soundEvent, Sound.Source.BLOCK, 1.0f, 0.9f + Random.nextFloat() * 0.1f), position)
        }
    }

    fun getSounds(material: Material): Pair<SoundEvent, SoundEvent>? {
        // See yarn: net.minecraft.block.BlockSetType or mojmap: net.minecraft.world.level.block.state.properties.BlockSetType
        return when (material) {
            Block.ACACIA_DOOR,
            Block.BIRCH_DOOR,
            Block.DARK_OAK_DOOR,
            Block.JUNGLE_DOOR,
            Block.MANGROVE_DOOR,
            Block.SPRUCE_DOOR,
            Block.OAK_DOOR -> SoundEvent.BLOCK_WOODEN_DOOR_OPEN to SoundEvent.BLOCK_WOODEN_DOOR_CLOSE

            Block.CHERRY_DOOR -> SoundEvent.BLOCK_CHERRY_WOOD_DOOR_OPEN to SoundEvent.BLOCK_CHERRY_WOOD_DOOR_CLOSE

            Block.CRIMSON_DOOR,
            Block.WARPED_DOOR -> SoundEvent.BLOCK_NETHER_WOOD_DOOR_OPEN to SoundEvent.BLOCK_NETHER_WOOD_DOOR_CLOSE


            Block.BAMBOO_DOOR -> SoundEvent.BLOCK_BAMBOO_WOOD_DOOR_OPEN to SoundEvent.BLOCK_BAMBOO_WOOD_DOOR_CLOSE

            Block.COPPER_DOOR,
            Block.EXPOSED_COPPER_DOOR,
            Block.WEATHERED_COPPER_DOOR,
            Block.OXIDIZED_COPPER_DOOR,
            Block.WAXED_COPPER_DOOR,
            Block.WAXED_EXPOSED_COPPER_DOOR,
            Block.WAXED_WEATHERED_COPPER_DOOR,
            Block.WAXED_OXIDIZED_COPPER_DOOR -> SoundEvent.BLOCK_COPPER_DOOR_OPEN to SoundEvent.BLOCK_COPPER_DOOR_CLOSE

            else -> null
        }
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