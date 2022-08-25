package com.bluedragonmc.server.command.punishment

import com.bluedragonmc.server.bootstrap.GlobalPunishments
import com.bluedragonmc.server.command.BlueDragonCommand
import com.bluedragonmc.server.command.OfflinePlayerArgument
import com.bluedragonmc.server.command.StringArrayArgument
import com.bluedragonmc.server.command.WordArgument
import com.bluedragonmc.server.event.DataLoadedEvent
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.database.PlayerDocument
import com.bluedragonmc.server.module.database.Punishment
import com.bluedragonmc.server.module.database.PunishmentType
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.exception.ArgumentSyntaxException
import java.util.*

class PunishCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    usage(usageString)

    val playerArgument by OfflinePlayerArgument
    val durationArgument by WordArgument
    val reasonArgument by StringArrayArgument

    syntax(playerArgument, durationArgument, reasonArgument) {
        val document = get(playerArgument)
        val duration = parseDuration(get(durationArgument))
        val reason = get(reasonArgument)
        val type = if (ctx.commandName.contains("ban")) PunishmentType.BAN else PunishmentType.MUTE
        val punishment = Punishment(type,
            UUID.randomUUID(),
            Date(),
            Date(System.currentTimeMillis() + duration),
            player.uuid,
            reason.joinToString(" "),
            active = true)

        DatabaseModule.IO.launch {
            document.compute(PlayerDocument::punishments) { punishments ->
                punishments.add(punishment)
                punishments
            }
            val target = getPlayer(playerArgument)
            target?.let {
                // If the player is on the server, call the DataLoadedEvent to send them the ban message
                MinecraftServer.getGlobalEventHandler().call(DataLoadedEvent(target))
                if(type == PunishmentType.MUTE) {
                    // Send a chat message telling the player they were muted.
                    it.sendMessage(GlobalPunishments.getMuteMessage(punishment))
                }
            }
            player.sendMessage(formatMessage("{} was ${if (type == PunishmentType.BAN) "banned" else "muted"} for {} for '{}'.",
                target?.name ?: document.username,
                get(durationArgument),
                reason.joinToString(" ")))
        }
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