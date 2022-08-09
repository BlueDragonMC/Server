package com.bluedragonmc.server.command

import com.bluedragonmc.server.module.gameplay.NPCModule
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin

class MindecraftesCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    usage(usageString)

    var isMindecraftes = false
    var previousSkin: PlayerSkin? = null
    syntax {
        if (sender is Player && sender.uuid.toString() == "110429e8-197f-4446-8bec-5d66f17be4d5") {
            if (isMindecraftes) {
                sender.skin = previousSkin
                sender.displayName = sender.username withColor (sender.displayName?.color() ?: NamedTextColor.WHITE)
                isMindecraftes = false
            } else {
                previousSkin = sender.skin
                sender.skin = NPCModule.NPCSkins.MINDECRAFTES.skin
                sender.displayName = "mindecraftes" withColor (sender.displayName?.color() ?: NamedTextColor.WHITE)
                isMindecraftes = true
            }
        } else {
            sender.sendMessage(formatErrorTranslated("command.mindecraftes.fail"))
        }
    }
})