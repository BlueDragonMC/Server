package com.bluedragonmc.server.module.gameplay

import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.CancellablePlayerEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.abilityProgressBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerPacketOutEvent
import net.minestom.server.event.player.PlayerStartFlyingEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket
import net.minestom.server.tag.Tag
import kotlin.math.cos
import kotlin.math.sin

/**
 * Allows players to double jump. The default settings are the ones used in the lobby.
 */
class DoubleJumpModule(
    private val strength: Double = 25.0,
    private val pitchInfluence: Double = 0.08,
    private val verticalStrength: Double = 10.0,
    private val cooldownMillis: Long = 0,
) : GameModule() {

    companion object {
        val LAST_DOUBLE_JUMP_TAG = Tag.Long("last_double_jump_timestamp")
        private val DOUBLE_JUMP_BLOCKERS_TAG: Tag<List<String>> = Tag.String("double_jump_blockers")
            .list()
            .defaultValue(emptyList())

        fun blockDoubleJump(player: Player, reason: String, updateFlying: Boolean = true) {
            val reasons = player.getTag(DOUBLE_JUMP_BLOCKERS_TAG).toMutableList().apply {
                if (!contains(reason)) add(reason)
            }
            player.setTag(DOUBLE_JUMP_BLOCKERS_TAG, reasons)
            if (updateFlying) player.isAllowFlying = false
        }

        fun unblockDoubleJump(player: Player, reason: String, updateFlying: Boolean = true) {
            val reasons = player.getTag(DOUBLE_JUMP_BLOCKERS_TAG).toMutableList().apply {
                remove(reason)
            }
            player.setTag(DOUBLE_JUMP_BLOCKERS_TAG, reasons)
            if (updateFlying && player.getTag(DOUBLE_JUMP_BLOCKERS_TAG).isEmpty()) {
                player.isAllowFlying = true
            }
        }
    }

    private fun canDoubleJump(player: Player) = !player.isAllowFlying &&
            isOffCooldown(player) &&
            player.getTag(DOUBLE_JUMP_BLOCKERS_TAG)?.isNotEmpty() != true

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerPacketOutEvent::class.java) { event ->
            if (event.packet is ChangeGameStatePacket && canDoubleJump(event.player)) {
                event.player.isAllowFlying = true
            }
        }
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (event.isOnGround && canDoubleJump(event.player))
                event.player.isAllowFlying = true
        }
        eventNode.addListener(PlayerStartFlyingEvent::class.java) { event ->
            if (event.player.gameMode == GameMode.CREATIVE || event.player.gameMode == GameMode.SPECTATOR) return@addListener
            event.player.isFlying = false
            event.player.isAllowFlying = false
            parent.callCancellable(PlayerDoubleJumpEvent(event.player)) {
                val x = -sin(Math.toRadians(event.player.position.yaw.toDouble())) * strength
                val y = verticalStrength + pitchInfluence * (-event.player.position.pitch.toDouble()).coerceAtLeast(0.0)
                val z = cos(Math.toRadians(event.player.position.yaw.toDouble())) * strength
                event.player.velocity = event.player.velocity.add(x, 0.0, z).withY(y)
                event.player.setTag(LAST_DOUBLE_JUMP_TAG, System.currentTimeMillis())
            }
        }
        if (cooldownMillis > 0)
            eventNode.addListener(PlayerTickEvent::class.java) { event ->
                val remainingMs = cooldownMillis - getTimeSinceLastJump(event.player)
                if (remainingMs >= 0) {
                    if (!parent.hasModule<ActionBarModule>()) {
                        event.player.sendActionBar(
                            abilityProgressBar(
                                Component.translatable("global.ability.double_jump", ALT_COLOR_1, TextDecoration.BOLD),
                                remainingMs.toInt(),
                                cooldownMillis.toInt()
                            )
                        )
                    }
                }
            }

            eventNode.addListener(ActionBarModule.CollectActionBarEvent::class.java) { event ->
                val remainingMs = cooldownMillis - getTimeSinceLastJump(event.player)
                if (remainingMs >= 0) {
                    event.addItem(
                        abilityProgressBar(
                            Component.translatable("global.ability.double_jump", ALT_COLOR_1, TextDecoration.BOLD),
                            remainingMs.toInt(),
                            cooldownMillis.toInt()
                        )
                    )
                }
            }
    }

    private fun getTimeSinceLastJump(player: Player): Long = System.currentTimeMillis() - getLastDoubleJump(player)

    private fun getLastDoubleJump(player: Player): Long {
        return if (player.hasTag(LAST_DOUBLE_JUMP_TAG)) player.getTag(LAST_DOUBLE_JUMP_TAG) else 0L
    }

    private fun isOffCooldown(player: Player): Boolean {
        val lastDoubleJump = getLastDoubleJump(player)
        return System.currentTimeMillis() - lastDoubleJump > cooldownMillis
    }

    class PlayerDoubleJumpEvent(player: Player) : CancellablePlayerEvent(player)
}