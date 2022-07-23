package com.bluedragonmc.server.command.punishment

import com.bluedragonmc.server.command.BlueDragonCommand
import com.bluedragonmc.server.command.WordArgument
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.PlayerDocument
import com.bluedragonmc.server.module.database.Punishment
import com.bluedragonmc.server.utils.surroundWithSeparators
import com.mongodb.client.model.Filters
import kotlinx.coroutines.launch
import org.litote.kmongo.elemMatch
import org.litote.kmongo.path

class ViewPunishmentCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {
        usage(usageString)

        val punishmentIDArgument by WordArgument

        syntax(punishmentIDArgument) {
            val id = get(punishmentIDArgument)

            DatabaseModule.IO.launch {
                val doc = DatabaseModule.getPlayersCollection()
                    .findOne(PlayerDocument::punishments elemMatch Filters.regex(Punishment::id.path(), "^$id"))
                if (doc == null) {
                    sender.sendMessage(formatMessage("No punishments found with ID {}.", id))
                    return@launch
                }
                val punishment = doc.punishments.first { it.id.toString().startsWith(id) }
                sender.sendMessage(formatMessage("Punishment ID: {}\nPlayer: {}\nReason: {}\nModerator: {}\nTime left: {}\nRevoked: {}",
                    punishment.id.toString().substringBefore('-'),
                    doc.username,
                    punishment.reason,
                    DatabaseModule.getPlayerDocument(punishment.moderator)?.username ?: "[${punishment.moderator}]",
                    if (punishment.isExpired()) "Expired" else punishment.getTimeRemaining(),
                    if (!punishment.active) "Yes" else "No"
                ).surroundWithSeparators())
            }
        }
    })