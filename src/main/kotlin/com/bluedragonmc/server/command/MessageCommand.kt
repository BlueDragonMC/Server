package com.bluedragonmc.server.command

import com.bluedragonmc.messages.ChatType
import com.bluedragonmc.messages.QueryPlayerMessage
import com.bluedragonmc.messages.SendChatMessage
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.utils.miniMessage
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player

class MessageCommand(name: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    val playerArgument by OptionalPlayerArgument
    val messageArgument by StringArrayArgument

    syntax(playerArgument, messageArgument) {
        val playerName = get(playerArgument)
        val player = MinecraftServer.getConnectionManager().getPlayer(playerName)
        val message = Component.text(get(messageArgument).joinToString(" "), NamedTextColor.GRAY)
        val senderName = (sender as? Player)?.name ?: Component.text("Console")
        if (player != null) {
            // Player is on the same server and online
            val color = (player as CustomPlayer).data.highestGroup?.color ?: NamedTextColor.GRAY
            val senderMessage = formatMessageTranslated("command.msg.sent", Component.text(playerName, color), message)
            val receiverMessage = formatMessageTranslated("command.msg.received", senderName, message)
            sender.sendMessage(senderMessage)
            player.sendMessage(receiverMessage)
        } else {
            runBlocking {
                val response = MessagingModule.send(QueryPlayerMessage(playerName, null))
                        as QueryPlayerMessage.Response
                if (!response.found) {
                    sender.sendMessage(formatMessageTranslated("command.msg.fail", playerName))
                    return@runBlocking
                }
                val color = DatabaseModule.getNameColor(response.uuid!!) ?: NamedTextColor.GRAY
                val senderMessage = formatMessageTranslated("command.msg.sent", Component.text(playerName, color), message)
                val receiverMessage = formatMessageTranslated("command.msg.received", senderName, message)
                sender.sendMessage(senderMessage)
                MessagingModule.publish(SendChatMessage(
                    response.uuid!!, miniMessage.serialize(receiverMessage), ChatType.CHAT
                ))
            }
        }
    }
})