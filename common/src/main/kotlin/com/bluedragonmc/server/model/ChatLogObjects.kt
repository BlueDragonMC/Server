package com.bluedragonmc.server.model

import kotlinx.serialization.Serializable
import java.util.*

/**
 * The kind of chat activity that was recorded in a [ChatLogDocument].
 */
@Serializable
enum class ChatLogType {
    CHAT,
    PARTY,
    PRIVATE,
    COMMAND,
}

/**
 * A player that was involved in a logged message (a party member or the recipient of a private message).
 */
@Serializable
data class ChatLogRecipient(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    val username: String = "",
)

/**
 * A single entry in the `chatLogs` collection. One document is stored per message or command.
 */
@Serializable
data class ChatLogDocument(
    @Serializable(with = UUIDSerializer::class) val playerUuid: UUID,
    val playerName: String = "",
    val type: ChatLogType,
    /** The ID of the game the sender was in, or null if they were not in a game. */
    val gameId: String? = null,
    /** The raw message or command text. */
    val message: String,
    /** The other players who saw the message. */
    val recipients: List<ChatLogRecipient> = emptyList(),
    val serverName: String = "",
    @Serializable(with = DateSerializer::class) val timestamp: Date = Date(),
)
