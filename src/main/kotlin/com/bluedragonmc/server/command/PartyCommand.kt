package com.bluedragonmc.server.command

import com.bluedragonmc.api.grpc.PartySvc
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.GameRegistry
import com.bluedragonmc.server.service.ChatLogger
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.service.Permissions
import com.bluedragonmc.server.utils.miniMessage
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.surroundWithSeparators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import java.util.*

/**
 * Resolves a possibly-offline player name to a UUID.
 * Returns `null` and notifies the sender if the player could not be found.
 */
private suspend fun BlueDragonCommand.CommandCtx.resolvePartyTarget(name: String): UUID? =
    withContext(Dispatchers.IO) { resolveOfflinePlayer(name)?.uuid }
        ?: run {
            sender.sendMessage(Component.translatable("argument.entity.notfound.player", NamedTextColor.RED))
            null
        }

private fun PartySvc.PartyListResponse.PlayerEntry.displayName(): Component =
    if (color.isBlank()) miniMessage.deserialize(username)
    else miniMessage.deserialize("<$color>$username")

class PartyCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {
        requirePlayers() // All subcommands require a player
        usage(usageString)

        /*
        invite <player>
        kick <player>
        leave
        promote <player>
        warp
        chat <message>
        list
        marathon start <minutes>
        marathon end
        marathon leaderboard
         */

        subcommand("invite") {
            val playerArgument by OfflinePlayerNameArgument
            suspendSyntax(playerArgument) {
                val uuid = resolvePartyTarget(get(playerArgument)) ?: return@suspendSyntax
                Messaging.outgoing.inviteToParty(player.uuid, uuid)
            }
        }

        subcommand("kick") {
            val playerArgument by OfflinePlayerNameArgument
            suspendSyntax(playerArgument) {
                val uuid = resolvePartyTarget(get(playerArgument)) ?: return@suspendSyntax
                Messaging.outgoing.kickFromParty(player.uuid, uuid)
            }
        }

        subcommand("leave") {
            suspendSyntax {
                Messaging.outgoing.leaveParty(player.uuid)
            }
        }

        subcommand("chat") {
            val messageArgument by StringArrayArgument
            suspendSyntax(messageArgument) {
                handlePartyChat(get(messageArgument).joinToString(" "), player)
            }
        }

        subcommand("accept") {
            val playerArgument by OfflinePlayerNameArgument
            suspendSyntax(playerArgument) {
                val uuid = resolvePartyTarget(get(playerArgument)) ?: return@suspendSyntax
                Messaging.outgoing.acceptPartyInvitation(uuid, player.uuid)
            }
        }

        subcommand("warp") {
            suspendSyntax {
                val gameId = GameRegistry.findGame(player)?.id
                if (gameId == null) {
                    sender.sendMessage(Component.translatable("puffin.party.warp.invalid_destination", errorColor))
                    return@suspendSyntax
                }
                Messaging.outgoing.warpParty(player, gameId)
            }
        }

        subcommand("transfer") {
            val playerArgument by OfflinePlayerNameArgument
            suspendSyntax(playerArgument) {
                val uuid = resolvePartyTarget(get(playerArgument)) ?: return@suspendSyntax
                Messaging.outgoing.transferParty(player, uuid)
            }
        }

        subcommand("list") {
            suspendSyntax {
                val response = Messaging.outgoing.listPartyMembers(player.uuid)
                if (response.playersCount > 0) {
                    val leader = response.playersList.find { it.role == PartySvc.PartyListResponse.Role.LEADER }
                    val members = response.playersList.filter { it != leader }
                    val leaderText = leader?.let {
                        formatMessageTranslated(
                            "puffin.party.list.leader",
                            it.displayName()
                        )
                    }
                    val membersText = formatMessageTranslated(
                        "puffin.party.list.members",
                        members.size,
                        Component.join(
                            JoinConfiguration.separator(Component.text(", ", BRAND_COLOR_PRIMARY_2)),
                            members.map { it.displayName() })
                    )
                    val text = if (leaderText != null) leaderText + Component.newline() + membersText else membersText
                    sender.sendMessage(text.surroundWithSeparators())
                } else {
                    sender.sendMessage(Component.translatable("puffin.party.list.not_found", NamedTextColor.RED))
                }
            }
        }

        subcommand("marathon") {
            usage("/party marathon <start|end|leaderboard> [...]")

            subcommand("start") {
                usage("/party marathon start [minutes]")
                val minutesArgument by IntArgument
                suspendSyntax(minutesArgument) {
                    val minutes = get(minutesArgument)
                    if (minutes in 5..300) {
                        Messaging.outgoing.startMarathon(player.uuid, get(minutesArgument) * 60 * 1_000)
                    } else {
                        sender.sendMessage(
                            Component.translatable(
                                "puffin.party.marathon.invalid_time",
                                NamedTextColor.RED
                            )
                        )
                    }
                }
            }

            subcommand("end") {
                suspendSyntax {
                    Messaging.outgoing.endMarathon(player.uuid)
                }
            }

            subcommand("leaderboard") {
                suspendSyntax {
                    Messaging.outgoing.getMarathonLeaderboard(setOf(player.uuid), false)
                }
            }
        }

        // If the player adds a player as the first argument instead of typing `invite <player>`
        val playerArgument by OfflinePlayerNameArgument
        suspendSyntax(playerArgument) {
            val uuid = resolvePartyTarget(get(playerArgument)) ?: return@suspendSyntax
            Messaging.outgoing.inviteToParty(player.uuid, uuid)
        }

    }) {
    // Party chat is recorded as ChatLogType.PARTY; so don't also log it as a command.
    override fun shouldLogAsCommand(arguments: String) =
        arguments.substringBefore(' ') != "chat"
}

suspend fun handlePartyChat(message: String, player: Player) {
    val msg = if (Permissions.hasPermission(player.uuid, "chat.minimessage") != true) {
        // Escape the chat message to prevent players using MiniMessage tags in party chat messages
        miniMessage.escapeTags(message)
    } else {
        // The player is allowed to use MiniMessage
        message
    }
    Messaging.outgoing.partyChat(msg, player)
    ChatLogger.logPartyChat(player, message)
}

class PartyChatShorthandCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {

        requirePlayers()
        usage(usageString)

        val messageArgument by StringArrayArgument
        suspendSyntax(messageArgument) {
            handlePartyChat(get(messageArgument).joinToString(" "), player)
        }
    }) {
    // Party chat is recorded as ChatLogType.PARTY, so don't also log it as a command.
    override fun shouldLogAsCommand(arguments: String) = false
}
