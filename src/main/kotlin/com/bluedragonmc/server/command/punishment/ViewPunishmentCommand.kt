package com.bluedragonmc.server.command.punishment

import com.bluedragonmc.server.command.BlueDragonCommand
import com.bluedragonmc.server.command.WordArgument
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.utils.surroundWithSeparators
import kotlinx.coroutines.launch

class ViewPunishmentCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {
        usage(usageString)

        val punishmentIDArgument by WordArgument

        syntax(punishmentIDArgument) {
            val id = get(punishmentIDArgument)

            Database.IO.launch {
                val doc = Database.connection.getPlayerForPunishmentId(id)
                if (doc == null) {
                    sender.sendMessage(formatMessageTranslated("command.view_punishment.not_found", id))
                    return@launch
                }
                val punishment = doc.punishments.first { it.id.toString().startsWith(id) }
                sender.sendMessage(formatMessageTranslated("command.view_punishment.response",
                    punishment.id.toString().substringBefore('-'),
                    doc.username,
                    punishment.reason,
                    Database.connection.getPlayerDocument(punishment.moderator)?.username ?: "[${punishment.moderator}]",
                    if (punishment.isExpired()) "Expired" else punishment.getTimeRemaining(),
                    if (!punishment.active) "Yes" else "No"
                ).surroundWithSeparators())
            }
        }
    })