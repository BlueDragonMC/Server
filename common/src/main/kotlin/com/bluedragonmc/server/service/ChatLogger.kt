package com.bluedragonmc.server.service

import com.bluedragonmc.server.GameRegistry
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.model.ChatLogDocument
import com.bluedragonmc.server.model.ChatLogRecipient
import com.bluedragonmc.server.model.ChatLogType
import kotlinx.coroutines.launch
import net.minestom.server.command.CommandSender
import net.minestom.server.entity.Player
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Persists chat messages and commands to the database.
 */
object ChatLogger {

    private val logger = LoggerFactory.getLogger(ChatLogger::class.java)

    /** The sender UUID used for messages that originate from the console. */
    val CONSOLE_UUID: UUID = UUID(0L, 0L)

    fun logChat(sender: Player, message: String, recipients: Collection<Player>) {
        write(
            ChatLogDocument(
                playerUuid = sender.uuid,
                playerName = sender.username,
                type = ChatLogType.CHAT,
                gameId = gameIdOf(sender),
                message = message,
                recipients = recipients
                    .filter { it.uuid != sender.uuid }
                    .map { ChatLogRecipient(it.uuid, it.username) },
            )
        )
    }

    fun logCommand(sender: Player, command: String) {
        write(
            ChatLogDocument(
                playerUuid = sender.uuid,
                playerName = sender.username,
                type = ChatLogType.COMMAND,
                gameId = gameIdOf(sender),
                message = command,
            )
        )
    }

    fun logPrivateMessage(sender: CommandSender, recipientUuid: UUID, recipientName: String, message: String) {
        val player = sender as? Player
        write(
            ChatLogDocument(
                playerUuid = player?.uuid ?: CONSOLE_UUID,
                playerName = player?.username ?: "Console",
                type = ChatLogType.PRIVATE,
                gameId = gameIdOf(player),
                message = message,
                recipients = listOf(ChatLogRecipient(recipientUuid, recipientName)),
            )
        )
    }

    fun logPartyChat(sender: Player, message: String) {
        val senderUuid = sender.uuid
        val senderName = sender.username
        val gameId = gameIdOf(sender)
        Database.IO.launch {
            val recipients = runCatching {
                Messaging.outgoing.listPartyMembers(senderUuid).playersList
                    .mapNotNull { entry ->
                        val uuid = runCatching { UUID.fromString(entry.uuid) }.getOrNull()
                            ?: return@mapNotNull null
                        if (uuid == senderUuid) null
                        else ChatLogRecipient(uuid, entry.username)
                    }
            }.getOrElse {
                logger.warn("Failed to look up party members while logging party chat for '$senderName'", it)
                emptyList()
            }
            writeNow(
                ChatLogDocument(
                    playerUuid = senderUuid,
                    playerName = senderName,
                    type = ChatLogType.PARTY,
                    gameId = gameId,
                    message = message,
                    recipients = recipients,
                )
            )
        }
    }

    private fun write(document: ChatLogDocument) {
        Database.IO.launch {
            writeNow(document)
        }
    }

    private fun gameIdOf(player: Player?): String? = player?.let { GameRegistry.findGame(it)?.id }

    private suspend fun writeNow(document: ChatLogDocument) {
        try {
            Database.connection.logChatMessage(document.copy(serverName = Environment.getServerName()))
        } catch (e: Throwable) {
            logger.error("Failed to write chat log for '${document.playerName}'", e)
        }
    }
}
