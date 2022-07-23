package com.bluedragonmc.server.command.punishment

import com.bluedragonmc.server.command.BlueDragonCommand
import com.bluedragonmc.server.command.OfflinePlayerArgument
import com.bluedragonmc.server.utils.buildComponent
import com.bluedragonmc.server.utils.clickEvent
import com.bluedragonmc.server.utils.surroundWithSeparators
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor

class ViewPunishmentsCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {

        usage(usageString)

        val playerArgument by OfflinePlayerArgument

        syntax(playerArgument) {
            val document = get(playerArgument)
            val player = getPlayer(playerArgument)

            sender.sendMessage(buildComponent {
                +("Punishments for " withColor messageColor)
                +(player?.name ?: (document.username withColor NamedTextColor.GRAY))
                +(" (${document.punishments.size})" withColor NamedTextColor.DARK_GRAY)
                +(": " withColor messageColor)
                for (punishment in document.punishments) {
                    val id = punishment.id.toString().substringBefore('-')
                    +Component.newline()
                    +("[$id] " withColor NamedTextColor.DARK_GRAY).clickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id)
                    +(punishment.type.toString() withColor fieldColor)
                    +(" - " withColor messageColor)
                    +((if (punishment.isInEffect()) "Expires in ${punishment.getTimeRemaining()}" else "Expired") withColor fieldColor)
                    +(" - " withColor messageColor)
                    +(punishment.reason withColor NamedTextColor.WHITE)
                }
            }.surroundWithSeparators())
        }
    })