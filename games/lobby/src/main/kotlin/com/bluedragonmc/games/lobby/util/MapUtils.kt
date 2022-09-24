package com.bluedragonmc.games.lobby.util

import com.bluedragonmc.server.utils.CoordinateUtils
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.ItemFrameMeta
import net.minestom.server.entity.metadata.other.ItemFrameMeta.Orientation
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.metadata.MapMeta
import net.minestom.server.map.framebuffers.LargeGraphics2DFramebuffer
import net.minestom.server.network.packet.server.play.MapDataPacket
import org.slf4j.LoggerFactory
import java.awt.Graphics2D
import java.util.function.Consumer
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min


object MapUtils {

    private val logger = LoggerFactory.getLogger(MapUtils::class.java)
    private var mapId = 0

    fun createMaps(instance: Instance, start: Pos, end: Pos, orientation: Orientation, startingMapId: Int = -1, renderFunction: Consumer<Graphics2D>) {
        val startTime = System.nanoTime()

        val minX = min(start.blockX(), end.blockX())
        val maxX = max(start.blockX(), end.blockX())

        val minY = min(start.blockY(), end.blockY())
        val maxY = max(start.blockY(), end.blockY())

        val minZ = min(start.blockZ(), end.blockZ())
        val maxZ = max(start.blockZ(), end.blockZ())

        val yaw = when (orientation) {
            Orientation.NORTH -> 180f
            Orientation.SOUTH -> 0f
            Orientation.WEST -> 90f
            Orientation.EAST -> -90f
            else -> 0f
        }

        val width = when (orientation) {
            Orientation.DOWN -> TODO()
            Orientation.UP -> TODO()
            Orientation.NORTH -> maxX - minX
            Orientation.SOUTH -> maxX - minX
            Orientation.WEST -> maxZ - minZ
            Orientation.EAST -> maxZ - minZ
        } + 1

        val height = when (orientation) {
            Orientation.DOWN -> TODO()
            Orientation.UP -> TODO()
            else -> maxY - minY
        } + 1

        logger.debug("Creating map board with width $width and height $height (${width * height} item frames).")

        val framebuffer = LargeGraphics2DFramebuffer(width * 128, height * 128)
        renderFunction.accept(framebuffer.renderer)
        val mapPackets = mapPackets(framebuffer, if (startingMapId < 0) mapId else startingMapId, width, height)

        val positions = CoordinateUtils.getAllInBox(start, end)
        for (pos in positions) {
            // The upper left corner is the [start] position.
            val relX = pos.blockX() - start.blockX()
            val relY = pos.blockY() - start.blockY()
            val relZ = pos.blockZ() - start.blockZ()
            val index = when (orientation) {
                Orientation.DOWN -> TODO()
                Orientation.UP -> TODO()
                Orientation.NORTH -> TODO()
                Orientation.SOUTH -> relY.absoluteValue * width + relX
                Orientation.WEST -> relY.absoluteValue * width + relZ.absoluteValue
                Orientation.EAST -> relY * width + relZ
            }.absoluteValue
            logger.trace("Creating map at position $pos (relative: $relX, $relY, $relZ) and index $index")
            // Remove old item frames that are already there
            for (entity in instance.getNearbyEntities(pos, 0.5)) {
                if (entity is MapItemFrame) {
                    entity.remove()
                }
            }
            // Create the new item frame entity
            MapItemFrame(orientation, mapPackets[index]!!).setInstance(instance, pos.withYaw(yaw))
        }

        if (startingMapId < 0) mapId += mapPackets.size
        logger.debug("Created ${mapPackets.size} maps in ${(System.nanoTime() - startTime) / 1_000_000}ms.")
    }

    /**
     * Creates packets for maps that will display an image on the board in the lobby
     */
    private fun mapPackets(framebuffer: LargeGraphics2DFramebuffer, startingMapId: Int = 0, width: Int, height: Int): Array<MapDataPacket?> {
        val numMaps = width * height
        val packets = arrayOfNulls<MapDataPacket>(numMaps)
        for (i in 0 until numMaps) {
            val x = i % width
            val y = i / width
            packets[i] = framebuffer.createSubView(x * 128, y * 128).preparePacket(startingMapId + i)
        }
        return packets
    }

    class MapItemFrame(orientation: Orientation, private val mapPacket: MapDataPacket): Entity(EntityType.ITEM_FRAME) {
        override fun updateNewViewer(player: Player) {
            super.updateNewViewer(player)
            player.sendPacket(mapPacket)
        }
        init {
            val meta = entityMeta as ItemFrameMeta
            meta.setNotifyAboutChanges(false)
            meta.orientation = orientation
            meta.isInvisible = true
            meta.item = ItemStack.builder(Material.FILLED_MAP)
                .meta(
                    MapMeta::class.java
                ) { builder: MapMeta.Builder -> builder.mapId(mapPacket.mapId) }
                .build()
            isAutoViewable = true
            meta.setNotifyAboutChanges(true)
        }

        override fun hasVelocity() = false
        override fun hasNoGravity() = true
    }
}