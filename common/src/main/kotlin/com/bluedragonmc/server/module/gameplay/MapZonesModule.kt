package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.trait.CancellableEvent
import net.minestom.server.event.trait.PlayerEvent

/**
 * Ties specific areas of the map to specific gameplay events.
 */
class MapZonesModule : GameModule() {
    val zones = mutableListOf<MapZone>()
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            zones.forEach { zone ->
                val oldPosInZone = zone.checkInZone(event.player.position)
                val newPosInZone = zone.checkInZone(event.newPosition)
                if (oldPosInZone && !newPosInZone) {
                    parent.callCancellable(PlayerPreLeaveZoneEvent(event.player, zone, event)) {
                        parent.callEvent(PlayerPostLeaveZoneEvent(event.player, zone, event))
                    }
                }
                else if (!oldPosInZone && newPosInZone) {
                    parent.callCancellable(PlayerPreEnterZoneEvent(event.player, zone, event)) {
                        parent.callEvent(PlayerPostEnterZoneEvent(event.player, zone, event))
                    }
                }
            }
        }
    }

    /**
     * Returns all zones with the specified label.
     */
    fun getZones(label: String) = zones.filter { it.labels.contains(label) }

    /**
     * Returns all zones that include the specified position.
     */
    fun getZones(position: Point) = zones.filter { it.checkInZone(position) }

    /**
     * Returns all zones with the specified label that include the specified position.
     */
    fun getZones(label: String, position: Point) = zones.filter { label in it.labels && it.checkInZone(position) }

    fun createZone(startPos: Point, endPos: Point, vararg labels: String) =
        MapZone(startPos, endPos, *labels, mapZonesModule = this).also { zones.add(it) }

    class MapZone(val startPos: Point, val endPos: Point, vararg val labels: String, mapZonesModule: MapZonesModule) {
        private val xRange = if (startPos.x() < endPos.x()) startPos.x()..endPos.x() else endPos.x()..startPos.x()
        private val yRange = if (startPos.y() < endPos.y()) startPos.y()..endPos.y() else endPos.y()..startPos.y()
        private val zRange = if (startPos.z() < endPos.z()) startPos.z()..endPos.z() else endPos.z()..startPos.z()
        val centerOnGround = endPos.add(startPos).mul(0.5).withY(yRange.start)
        val players = mutableListOf<Player>()

        /**
         * An event node that accepts ZoneEvents with the right zone and EntityEvents where the entity is inside the zone.
         */
        val eventNode = EventNode.event(
            "${labels.joinToString(",", "[", "]")}_${this::class.simpleName}",
            EventFilter.ALL
        ) { event ->
            if (event is ZoneEvent) {
                return@event event.zone === this
            }
            if (event is PlayerEvent) return@event checkInZone(event.entity.position)
            false
        }

        init {
            mapZonesModule.eventNode!!.addChild(eventNode)
        }

        /**
         * Checks if the specified [position] is in this [CombatZone].
         */
        fun checkInZone(position: Point): Boolean {
            return position.x() in xRange && position.y() in yRange && position.z() in zRange
        }

        override fun toString(): String {
            return "MapZone(startPos=$startPos, endPos=$endPos, labels=${labels.contentToString()}, players=$players)"
        }
    }

    abstract class ZoneEvent(open val zone: MapZone)

    open class PlayerZoneEvent(private val player: Player, override val zone: MapZone, val moveEvent: PlayerMoveEvent) : PlayerEvent, ZoneEvent(zone) {
        override fun getPlayer(): Player = player
    }

    open class CancellablePlayerZoneEvent(private val player: Player, override val zone: MapZone, val moveEvent: PlayerMoveEvent) : PlayerEvent,
        CancellableEvent, ZoneEvent(zone) {
        override fun getPlayer(): Player = player
        private var cancelled = false

        override fun isCancelled(): Boolean = cancelled

        override fun setCancelled(cancel: Boolean) {
            cancelled = cancel
        }
    }

    /**
     * Cancellable event to check if the player has permission to enter the zone.
     */
    class PlayerPreEnterZoneEvent(player: Player, zone: MapZone, moveEvent: PlayerMoveEvent) : CancellablePlayerZoneEvent(player, zone, moveEvent)

    /**
     * Called after a player enters the zone.
     */
    class PlayerPostEnterZoneEvent(player: Player, zone: MapZone, moveEvent: PlayerMoveEvent) : PlayerZoneEvent(player, zone, moveEvent)

    /**
     * Cancellable event to check if the player has permission to leave the zone.
     */
    class PlayerPreLeaveZoneEvent(player: Player, zone: MapZone, moveEvent: PlayerMoveEvent) : CancellablePlayerZoneEvent(player, zone, moveEvent)

    /**
     * Called after the player leaves the zone.
     */
    class PlayerPostLeaveZoneEvent(player: Player, zone: MapZone, moveEvent: PlayerMoveEvent) : PlayerZoneEvent(player, zone, moveEvent)
}