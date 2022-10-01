package com.bluedragonmc.games.lobby

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.CosmeticsModule
import com.bluedragonmc.server.module.gameplay.DoubleJumpModule
import com.bluedragonmc.server.module.gameplay.MapZonesModule
import com.bluedragonmc.server.utils.packet.PacketUtils
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.particle.Particle
import kotlin.math.cos
import kotlin.math.sin

@DependsOn(CosmeticsModule::class)
class LobbyCosmeticsModule : GameModule() {

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        val cosmetics = parent.getModule<CosmeticsModule>()

        eventNode.addListener(DoubleJumpModule.PlayerDoubleJumpEvent::class.java) { event ->
            when (cosmetics.getCosmeticInGroup<DoubleJumpEffect>(event.player)) {
                DoubleJumpEffect.DOUBLE_JUMP_NOTE -> {
                    (0 until 360 step 36).forEach { degrees ->
                        val radians = Math.toRadians(degrees.toDouble())
                        val packet = PacketUtils.createParticlePacket(
                            event.player.position.add(
                                cos(radians) * 2.5, 0.0, sin(radians) * 2.5
                            ), Particle.NOTE, 2
                        )
                        event.player.sendPacketToViewersAndSelf(packet)
                    }
                }
                null -> {}
            }
        }

        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            event.player.helmet = cosmetics.getCosmeticInGroup<LobbyHat>(event.player)?.itemStack ?: ItemStack.of(Material.AIR)
        }

        eventNode.addListener(CosmeticsModule.PlayerEquipCosmeticEvent::class.java) { event ->
            event.player.helmet = cosmetics.getCosmeticInGroup<LobbyHat>(event.player)?.itemStack ?: ItemStack.of(Material.AIR)
        }

        eventNode.addListener(CosmeticsModule.PlayerUnequipCosmeticEvent::class.java) { event ->
            event.player.helmet = cosmetics.getCosmeticInGroup<LobbyHat>(event.player)?.itemStack ?: ItemStack.of(Material.AIR)
        }

        eventNode.addListener(MapZonesModule.PlayerPostLeaveZoneEvent::class.java) { event ->
            event.player.helmet = cosmetics.getCosmeticInGroup<LobbyHat>(event.player)?.itemStack ?: ItemStack.of(Material.AIR)
        }
    }

    enum class DoubleJumpEffect(override val id: String) : CosmeticsModule.Cosmetic {
        DOUBLE_JUMP_NOTE("lobby_double_jump_note")
    }

    enum class LobbyHat(override val id: String, val itemStack: ItemStack) : CosmeticsModule.Cosmetic {
        GLASS("lobby_hat_glass", ItemStack.of(Material.GLASS)),
        ICE("lobby_hat_ice", ItemStack.of(Material.ICE)),
        SLIME("lobby_hat_slime", ItemStack.of(Material.SLIME_BLOCK)),
        HONEY("lobby_hat_honey", ItemStack.of(Material.HONEY_BLOCK)),
        JACK_O_LANTERN("lobby_hat_jack_o_lantern", ItemStack.of(Material.JACK_O_LANTERN)),
        COMPASS("lobby_hat_compass", ItemStack.of(Material.COMPASS)),
        CLOCK("lobby_hat_clock", ItemStack.of(Material.CLOCK)),
        NETHERITE_HELMET("lobby_hat_netherite_helmet", ItemStack.of(Material.NETHERITE_HELMET)),
        CREEPER_HEAD("lobby_hat_creeper_head", ItemStack.of(Material.CREEPER_HEAD)),
        ZOMBIE_HEAD("lobby_hat_zombie_head", ItemStack.of(Material.ZOMBIE_HEAD)),
        STEVE_HEAD("lobby_hat_steve_head", ItemStack.of(Material.PLAYER_HEAD)),
        SKELETON_SKULL("lobby_hat_skeleton_skull", ItemStack.of(Material.SKELETON_SKULL)),
        WITHER_SKELETON_SKULL("lobby_hat_wither_skeleton_skull", ItemStack.of(Material.WITHER_SKELETON_SKULL)),
        DRAGON_HEAD("lobby_hat_dragon_head", ItemStack.of(Material.DRAGON_HEAD)),

    }
}