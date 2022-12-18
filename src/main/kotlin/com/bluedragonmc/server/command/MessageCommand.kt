package com.bluedragonmc.server.command

import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.service.Permissions
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.util.*

class MessageCommand(name: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    val playerArgument by OptionalPlayerArgument
    val messageArgument by StringArrayArgument

    syntax(playerArgument, messageArgument) {
        val playerName = get(playerArgument)
        val player = MinecraftServer.getConnectionManager().getPlayer(playerName)
        val message = Component.text(get(messageArgument).joinToString(" "), NamedTextColor.GRAY)
        val senderName = (sender as? Player)?.name ?: Component.translatable("command.msg.console")
        if (player != null) {
            // Player is on the same server and online
            val color = Permissions.getMetadata(player.uuid).rankColor
            val senderMessage = formatMessageTranslated("command.msg.sent", Component.text(playerName, color), message)
            val receiverMessage = formatMessageTranslated("command.msg.received", senderName, message)
            sender.sendMessage(senderMessage)
            player.sendMessage(receiverMessage)
        } else {
            runBlocking {
                val recipient = Messaging.outgoing.queryPlayer(username = playerName)
                if (!recipient.isOnline) {
                    sender.sendMessage(formatMessageTranslated("command.msg.fail", playerName))
                    return@runBlocking
                }
                val recipientUuid = UUID.fromString(recipient.uuid!!)
                val color = Permissions.getMetadata(recipientUuid).rankColor
                val senderMessage = formatMessageTranslated("command.msg.sent", Component.text(playerName, color), message)
                Messaging.outgoing.sendPrivateMessage(message, sender, recipientUuid)
                sender.sendMessage(senderMessage)
            }
        }
    }
})