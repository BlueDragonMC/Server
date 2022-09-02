package com.bluedragonmc.server.block

import com.bluedragonmc.server.NAMESPACE
import com.bluedragonmc.server.module.GuiModule
import com.bluedragonmc.server.utils.displayName
import com.bluedragonmc.server.utils.noItalic
import net.kyori.adventure.text.Component
import net.minestom.server.effects.Effects
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.BlockHandler
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material
import net.minestom.server.utils.NamespaceID

/**
 * When a player right clicks the block, a menu opens that allows them to select a song.
 * When the player chooses a song, it is played to everyone around them.
 */
class JukeboxMenuBlockHandler(val instance: Instance, val x: Int, val y: Int, val z: Int) : BlockHandler {
        private val guiModule = GuiModule()
        val menu = guiModule.createMenu(
            title = Component.text("Select a Song"),
            inventoryType = InventoryType.CHEST_3_ROW,
            isPerPlayer = false,
            allowSpectatorClicks = false
        ) {
            val materials = Material.values()
            println(materials)
            println(materials.size)
            var i = 0
            materials.forEach { disc ->
                if (!disc.name().startsWith("minecraft:music_disc_")) return@forEach
                this.slot(i++, disc, { player ->
                    displayName(disc.displayName().noItalic())
                }) {
                    instance.players.forEach { it.playEffect(Effects.PLAY_RECORD, x, y, z, disc.id(), false) }
                    menu.close(player)
                }
            }
        }

        override fun getNamespaceId(): NamespaceID = NamespaceID.from("$NAMESPACE:jukebox_handler")
        override fun onInteract(interaction: BlockHandler.Interaction): Boolean {
            menu.open(interaction.player)
            return false
        }
    }