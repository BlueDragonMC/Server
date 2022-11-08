package com.bluedragonmc.server.command

import com.bluedragonmc.api.grpc.playerQueryRequest
import com.bluedragonmc.api.grpc.privateMessageRequest
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.utils.miniMessage
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
            val color = (player as CustomPlayer).data.highestGroup?.color ?: NamedTextColor.GRAY
            val senderMessage = formatMessageTranslated("command.msg.sent", Component.text(playerName, color), message)
            val receiverMessage = formatMessageTranslated("command.msg.received", senderName, message)
            sender.sendMessage(senderMessage)
            player.sendMessage(receiverMessage)
        } else {
            runBlocking {
                val response = MessagingModule.Stubs.playerTrackerStub.queryPlayer(playerQueryRequest {
                    username = playerName
                })
                if (!response.isOnline) {
                    sender.sendMessage(formatMessageTranslated("command.msg.fail", playerName))
                    return@runBlocking
                }
                val color = DatabaseModule.getNameColor(UUID.fromString(response.uuid!!)) ?: NamedTextColor.GRAY
                val senderMessage = formatMessageTranslated("command.msg.sent", Component.text(playerName, color), message)
                MessagingModule.Stubs.privateMessageStub.sendMessage(privateMessageRequest {
                    this.message = miniMessage.serialize(message)
                    this.recipientUuid = response.uuid
                    (sender as? Player)?.username?.let { this.senderUsername = it }
                    this.senderUuid = (sender as? Player)?.uuid?.toString() ?: UUID(0L, 0L).toString()
                })
                sender.sendMessage(senderMessage)
            }
        }
    }
})