package com.bluedragonmc.server.command

import com.bluedragonmc.server.module.gameplay.NPCModule
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.PlayerSkin

class MindecraftesCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    usage(usageString)
    requirePlayers()

    var isMindecraftes = false
    var previousSkin: PlayerSkin? = null
    syntax {
        if (player.uuid.toString() == "110429e8-197f-4446-8bec-5d66f17be4d5") {
            if (isMindecraftes) {
                player.skin = previousSkin
                player.displayName = player.username withColor (player.displayName?.color() ?: NamedTextColor.WHITE)
                isMindecraftes = false
            } else {
                previousSkin = player.skin
                player.skin = NPCModule.NPCSkins.MINDECRAFTES.skin
                player.displayName = "mindecraftes" withColor (player.displayName?.color() ?: NamedTextColor.WHITE)
                isMindecraftes = true
            }
        } else {
            player.sendMessage(formatErrorTranslated("command.mindecraftes.fail"))
        }
    }
})