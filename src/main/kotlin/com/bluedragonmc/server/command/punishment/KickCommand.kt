package com.bluedragonmc.server.command.punishment

import com.bluedragonmc.server.command.BlueDragonCommand
import com.bluedragonmc.server.command.PlayerArgument
import com.bluedragonmc.server.command.StringArrayArgument
import com.bluedragonmc.server.utils.buildComponent
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player

class KickCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    usage(usageString)

    val playerArgument by PlayerArgument
    val reasonArgument by StringArrayArgument

    syntax(playerArgument, reasonArgument) {
        val target = getFirstPlayer(playerArgument)
        val reason = get(reasonArgument).joinToString(" ")

        target.kick(buildComponent {
            +("You have been kicked from the server!" withColor NamedTextColor.RED)
            +Component.newline()
            +Component.newline()
            +("Kicked by: " withColor NamedTextColor.RED)
            +if (sender is Player) (sender as Player).name else "Console" withColor NamedTextColor.WHITE
            +Component.newline()
            +("Reason: " withColor NamedTextColor.RED)
            +(reason withColor NamedTextColor.WHITE)
            // Kick messages with a non-breaking space (U+00A0) will prevent the player from being immediately connected to another server
            // This is a way of differentiating intentional vs. accidental kicks that remains invisible to the end user
            +Component.text("\u00A0")
        })
        sender.sendMessage(formatMessageTranslated("command.kick.success", target.name, reason))
    }

})