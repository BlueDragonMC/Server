package com.bluedragonmc.server.command.punishment

import com.bluedragonmc.server.bootstrap.GlobalPunishments
import com.bluedragonmc.server.command.*
import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.model.PlayerDocument
import com.bluedragonmc.server.model.Punishment
import com.bluedragonmc.server.model.PunishmentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.exception.ArgumentSyntaxException
import java.util.*

class PunishCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {

        usage(usageString)

        val playerArgument by OfflinePlayerNameArgument
        val durationArgument by WordArgument
        val reasonArgument by StringArrayArgument

        suspendSyntax(playerArgument, durationArgument, reasonArgument) {
            val playerName = get(playerArgument)
            val duration = parseDuration(get(durationArgument))
            val reason = get(reasonArgument)
            val type = if (ctx.commandName.contains("ban")) PunishmentType.BAN else PunishmentType.MUTE
            val punishment = Punishment(
                type,
                UUID.randomUUID(),
                Date(),
                Date(System.currentTimeMillis() + duration),
                player.uuid,
                reason.joinToString(" "),
                active = true
            )

            val document = withContext(Dispatchers.IO) {
                val doc = resolveOfflinePlayer(playerName)
                doc?.compute(PlayerDocument::punishments) { punishments ->
                    punishments.add(punishment)
                    punishments
                }
                doc
            }
            if (document == null) {
                sender.sendMessage(
                    formatErrorTranslated("argument.entity.notfound.player", playerName)
                )
                return@suspendSyntax
            }

            val target = MinecraftServer.getConnectionManager()
                .getOnlinePlayerByUuid(document.uuid)
            target?.let {
                // If the player is on the server, call the DataLoadedEvent to send them the ban message
                MinecraftServer.getGlobalEventHandler().call(DataLoadedEvent(target))
                if (type == PunishmentType.MUTE) {
                    // Send a chat message telling the player they were muted.
                    it.sendMessage(GlobalPunishments.getMuteMessage(punishment))
                }
            }
            player.sendMessage(
                formatMessageTranslated(
                    if (type === PunishmentType.BAN) "command.ban.success" else "command.mute.success",
                    target?.name ?: document.username,
                    get(durationArgument),
                    reason.joinToString(" ")
                )
            )
        }
    }) {
    companion object {
        fun parseDuration(input: String): Long {
            val split = input.split(" ")
            return split.sumOf { part ->
                val multiplier = when (part.last()) {
                    'y' -> 31536000L
                    'd' -> 86400L
                    'h' -> 3600L
                    'm' -> 60L
                    's' -> 1L
                    else -> throw ArgumentSyntaxException("Invalid date string", input, -1)
                }
                part.dropLast(1).toLong() * multiplier * 1000L
            }
        }
    }
}