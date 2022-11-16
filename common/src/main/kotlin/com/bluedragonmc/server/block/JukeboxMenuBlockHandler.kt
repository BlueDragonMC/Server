package com.bluedragonmc.server.block

import com.bluedragonmc.server.NAMESPACE
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.utils.displayName
import com.bluedragonmc.server.utils.noItalic
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.effects.Effects
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.EffectPacket
import net.minestom.server.network.packet.server.play.StopSoundPacket
import net.minestom.server.utils.NamespaceID

/**
 * When a player right-clicks the block, a menu opens that allows them to select a song.
 * When the player chooses a song, it is played to everyone around them.
 */
class JukeboxMenuBlockHandler(val instance: Instance, val x: Int, val y: Int, val z: Int) : BlockHandler {
    init {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) { event ->
            if (mostRecentSong != null) event.player.sendPacket(
                StopSoundPacket(
                    0,
                    Sound.Source.BLOCK,
                    mostRecentSong
                )
            )
        }
    }

    private var mostRecentSong: String? = null
    private val guiModule = GuiModule()

    val menu = guiModule.createMenu(
        title = Component.translatable("lobby.menu.jukebox.title"),
        inventoryType = InventoryType.CHEST_3_ROW,
        isPerPlayer = false,
        allowSpectatorClicks = false
    ) {
        Material.values()
            .filter { it.name().startsWith("minecraft:music_disc_") }
            .sortedBy { it.name() }
            .forEachIndexed { i, disc ->
            slot(i, disc, {
                displayName(disc.displayName().noItalic())
            }) {
                instance.sendGroupedPacket(
                    EffectPacket(Effects.PLAY_RECORD.id, Vec(x.toDouble(), y.toDouble(), z.toDouble()), disc.id(), false)
                )
                mostRecentSong = discToSound(disc.name())
                menu.close(player)
            }
        }
    }

    override fun getNamespaceId(): NamespaceID = NamespaceID.from("$NAMESPACE:jukebox")

    override fun onInteract(interaction: BlockHandler.Interaction): Boolean {
        menu.open(interaction.player)
        return false
    }

    private fun discToSound(discName: String): String =
        "minecraft:music_disc.${discName.substringAfter("minecraft:music_disc_")}"
}