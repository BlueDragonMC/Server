package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.utils.manage
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerRespawnEvent
import java.time.Duration

/**
 * Disallows combat unless the attacker and target are in any combat zone.
 * When the module is initialized, any map zones with the label "Combat" are added as combat zones.
 * Optionally, the module prevents the player from leaving the zone if they are in a fight.
 * Requires the [MapZonesModule]
 */
@DependsOn(MapZonesModule::class, OldCombatModule::class)
class CombatZonesModule(
    val allowLeaveDuringCombat: Boolean,
    val minCombatSeconds: Int,
    private val startingCombatZones: MutableList<MapZonesModule.MapZone> = mutableListOf(),
) : GameModule() {

    private val combatStatus = hashMapOf<Player, Int>()

    private lateinit var mapZonesModule: MapZonesModule
    private lateinit var parent: Game

    private val combatZones = mutableListOf<MapZonesModule.MapZone>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        mapZonesModule = parent.getModule()
        startingCombatZones.forEach { zone ->
            addCombatZone(zone)
        }
        mapZonesModule.getZones("Combat").forEach { zone ->
            addCombatZone(zone)
        }

        eventNode.addListener(OldCombatModule.PlayerAttackEvent::class.java) { event ->
            if (event.target !is Player) return@addListener
            if (!checkInZone(event.target.position) || !checkInZone(event.attacker.position)) event.isCancelled = true
            else {
                combatStatus[event.target] = 0
                combatStatus[event.attacker] = 0
            }
        }

        if (!allowLeaveDuringCombat)
            MinecraftServer.getSchedulerManager().buildTask {
                for (s in combatStatus) {
                    combatStatus[s.key] = combatStatus.getOrDefault(s.key, 1000) + 1
                }
            }.repeat(Duration.ofSeconds(1)).schedule().manage(parent)
    }

    fun addCombatZone(zone: MapZonesModule.MapZone) {
        combatZones.add(zone)
        zone.eventNode.addListener(MapZonesModule.PlayerPreLeaveZoneEvent::class.java) { event ->
            if (!allowLeaveDuringCombat && combatStatus.getOrDefault(event.player, 1000) < minCombatSeconds) {
                event.player.sendActionBar("You cannot leave while in combat!" withColor NamedTextColor.RED)
                event.player.velocity = event.player.position.sub(event.zone.centerOnGround.x(),
                    event.zone.centerOnGround.y(),
                    event.zone.centerOnGround.z()).mul(-1.5).withY(4.0).asVec()
                event.isCancelled = true
                return@addListener
            }
        }
        zone.eventNode.addListener(MapZonesModule.PlayerPostEnterZoneEvent::class.java) { event ->
            if (parent.hasModule<DoubleJumpModule>())
               DoubleJumpModule.blockDoubleJump(event.player, "combat")
        }
        zone.eventNode.addListener(MapZonesModule.PlayerPostLeaveZoneEvent::class.java) { event ->
            if (parent.hasModule<DoubleJumpModule>())
                DoubleJumpModule.unblockDoubleJump(event.player, "combat")
        }
        eventNode.addListener(PlayerRespawnEvent::class.java) { event ->
            if (!checkInZone(event.respawnPosition) && parent.hasModule<DoubleJumpModule>()) {
                DoubleJumpModule.unblockDoubleJump(event.player, "combat")
            }
        }
    }

    /**
     * Returns true if the specified [point] is inside any combat zone.
     */
    fun checkInZone(point: Point): Boolean = combatZones.any { it.checkInZone(point) }

    data class CombatZone(val startPos: Point, val endPos: Point) {
        private val xRange = if (startPos.x() < endPos.x()) startPos.x()..endPos.x() else endPos.x()..startPos.x()
        private val yRange = if (startPos.y() < endPos.y()) startPos.y()..endPos.y() else endPos.y()..startPos.y()
        private val zRange = if (startPos.z() < endPos.z()) startPos.z()..endPos.z() else endPos.z()..startPos.z()

        /**
         * Checks if the specified [position] is in this [CombatZone].
         */
        fun checkInZone(position: Point): Boolean {
            return position.x() in xRange &&
                    position.y() in yRange &&
                    position.z() in zRange
        }
    }

}