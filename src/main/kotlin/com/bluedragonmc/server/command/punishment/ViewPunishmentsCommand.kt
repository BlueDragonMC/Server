package com.bluedragonmc.server.command.punishment

import com.bluedragonmc.server.command.BlueDragonCommand
import com.bluedragonmc.server.command.OfflinePlayerNameArgument
import com.bluedragonmc.server.command.resolveOfflinePlayer
import com.bluedragonmc.server.utils.buildComponent
import com.bluedragonmc.server.utils.clickEvent
import com.bluedragonmc.server.utils.surroundWithSeparators
import com.bluedragonmc.server.utils.withColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer

class ViewPunishmentsCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {

        usage(usageString)

        val playerArgument by OfflinePlayerNameArgument

        suspendSyntax(playerArgument) {
            val playerName = get(playerArgument)

            val document = withContext(Dispatchers.IO) { resolveOfflinePlayer(playerName) }
            if (document == null) {
                sender.sendMessage(formatErrorTranslated("argument.entity.notfound.player", playerName))
                return@suspendSyntax
            }
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(document.uuid)

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