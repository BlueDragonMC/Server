package com.bluedragonmc.server.command.punishment

import com.bluedragonmc.server.command.BlueDragonCommand
import com.bluedragonmc.server.command.OfflinePlayerNameArgument
import com.bluedragonmc.server.command.resolveOfflinePlayer
import com.bluedragonmc.server.model.PlayerDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PardonCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    /*
    TODO: Make another syntax that receives a punishment ID instead of an offline player, using the following query:
    DatabaseModule.getPlayersCollection().findOne(PlayerDocument::punishments elemMatch Filters.regex(Punishment::id.path(), "^$input"))
    ** create an index in MongoDB for punishment IDs and force the ban ID input to be the correct length
     */

    usage(usageString)

    val playerArgument by OfflinePlayerNameArgument
    suspendSyntax(playerArgument) {
        val playerName = get(playerArgument)

        val document = withContext(Dispatchers.IO) { resolveOfflinePlayer(playerName) }
        if (document == null) {
            sender.sendMessage(formatErrorTranslated("argument.entity.notfound.player", playerName))
            return@suspendSyntax
        }

        withContext(Dispatchers.IO) {
            document.compute(PlayerDocument::punishments) { punishments ->
                punishments.forEach {
                    if (it.isInEffect()) {
                        sender.sendMessage(formatMessageTranslated(
                            "command.pardon.success.${it.type.toString().lowercase()}",
                                it.id.toString().substringBefore('-'))
                        )
                        it.active = false
                    }
                }
                punishments
            }
            sender.sendMessage(formatMessageTranslated("command.pardon.completed"))
        }
    }
})