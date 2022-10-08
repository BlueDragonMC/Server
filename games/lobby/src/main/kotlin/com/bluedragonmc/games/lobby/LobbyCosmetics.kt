package com.bluedragonmc.games.lobby

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.CosmeticsModule
import com.bluedragonmc.server.module.gameplay.DoubleJumpModule
import com.bluedragonmc.server.module.gameplay.MapZonesModule
import com.bluedragonmc.server.utils.packet.PacketUtils
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.event.trait.PlayerEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.ServerPacket
import net.minestom.server.particle.Particle
import net.minestom.server.tag.Tag
import kotlin.math.cos
import kotlin.math.sin

@DependsOn(CosmeticsModule::class)
class LobbyCosmeticsModule : GameModule() {

    companion object {
        private val COSMETIC_TAG = Tag.Boolean("is_cosmetic")
        private val stainedGlassBlocks = Material.values().filter {
            it.name().endsWith("stained_glass")
        }.map {
            ItemStack.of(it).cosmetic()
        }

        private fun isCosmeticItem(itemStack: ItemStack) = itemStack.hasTag(COSMETIC_TAG)
        private fun ItemStack.cosmetic() = withTag(COSMETIC_TAG, true)
    }

    private lateinit var cosmetics: CosmeticsModule

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        cosmetics = parent.getModule<CosmeticsModule>()

        cosmetics.handleEvent<DoubleJumpModule.PlayerDoubleJumpEvent>(DoubleJumpEffect.NOTE) { event ->
            playCircularEffect(event.player) { pos ->
                PacketUtils.createParticlePacket(pos, Particle.NOTE, 2)
            }
        }

        cosmetics.handleEvent<DoubleJumpModule.PlayerDoubleJumpEvent>(DoubleJumpEffect.CLOUD) { event ->
            playCircularEffect(event.player) { pos ->
                PacketUtils.createParticlePacket(pos, Particle.CLOUD, 5)
            }
        }

        cosmetics.handleEvent<PlayerTickEvent>(LobbyHat.RAINBOW) { event ->
            if (event.player.aliveTicks % 4 == 0L && isCosmeticItem(event.player.helmet)) {
                event.player.helmet =
                    stainedGlassBlocks[(event.player.aliveTicks % 4).toInt() % stainedGlassBlocks.size]
            }
        }

        eventNode.addListener(PlayerSpawnEvent::class.java, ::updateHat)
        eventNode.addListener(CosmeticsModule.PlayerEquipCosmeticEvent::class.java, ::updateHat)
        eventNode.addListener(CosmeticsModule.PlayerUnequipCosmeticEvent::class.java, ::updateHat)
        eventNode.addListener(MapZonesModule.PlayerPostLeaveZoneEvent::class.java, ::updateHat)
    }

    enum class DoubleJumpEffect(override val id: String) : CosmeticsModule.Cosmetic {
        NOTE("lobby_double_jump_note"),
        CLOUD("lobby_double_jump_cloud"),
    }

    enum class LobbyHat(override val id: String, val itemStack: ItemStack) : CosmeticsModule.Cosmetic {
        GLASS("lobby_hat_glass", ItemStack.of(Material.GLASS)),
        RAINBOW("lobby_hat_rainbow", ItemStack.of(Material.BLUE_STAINED_GLASS)),
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

    private fun playCircularEffect(player: Player, generator: (Pos) -> ServerPacket) =
        playCircularEffect(player, player.position, generator)

    private fun playCircularEffect(player: Player, pos: Pos, generator: (Pos) -> ServerPacket) {
        (0 until 360 step 36).forEach { degrees ->
            val radians = Math.toRadians(degrees.toDouble())
            val packet = generator(pos.add(cos(radians) * 2.5, 0.0, sin(radians) * 2.5))
            player.sendPacketToViewersAndSelf(packet)
        }
    }

    private fun updateHat(event: PlayerEvent) {
        event.player.helmet = getHat(event.player)
    }

    private fun getHat(player: Player) = cosmetics.getCosmeticInGroup<LobbyHat>(player)?.itemStack?.cosmetic()
        ?: ItemStack.of(Material.AIR)
}