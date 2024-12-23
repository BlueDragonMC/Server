package com.bluedragonmc.server.command

import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.service.Permissions
import com.bluedragonmc.server.utils.miniMessage
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player

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
            val playerArgument by OfflinePlayerArgument
            suspendSyntax(playerArgument) {
                Messaging.outgoing.inviteToParty(player.uuid, get(playerArgument).uuid)
            }
        }

        subcommand("kick") {
            val playerArgument by OfflinePlayerArgument
            suspendSyntax(playerArgument) {
                Messaging.outgoing.kickFromParty(player.uuid, get(playerArgument).uuid)
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
            val playerArgument by OfflinePlayerArgument
            suspendSyntax(playerArgument) {
                Messaging.outgoing.acceptPartyInvitation(get(playerArgument).uuid, player.uuid)
            }
        }

        subcommand("warp") {
            suspendSyntax {
                Messaging.outgoing.warpParty(player, player.instance!!)
            }
        }

        subcommand("transfer") {
            val playerArgument by OfflinePlayerArgument
            suspendSyntax(playerArgument) {
                Messaging.outgoing.transferParty(player, get(playerArgument).uuid)
            }
        }

        subcommand("list") {
            suspendSyntax {
                val response = Messaging.outgoing.listPartyMembers(player.uuid)
                val leader = response.playersList.find { it.role == "Leader" }
                val members = response.playersList.filter { it != leader }
                if (leader != null && response.playersCount > 0) {
                    val leaderText = formatMessageTranslated(
                        "puffin.party.list.leader",
                        miniMessage.deserialize(leader.username)
                    )
                    val membersText = formatMessageTranslated(
                        "puffin.party.list.members",
                        members.size,
                        *members.map { miniMessage.deserialize(it.username) }.toTypedArray()
                    )
                    sender.sendMessage((leaderText + Component.newline() + membersText).surroundWithSeparators())
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
                        sender.sendMessage(Component.translatable("puffin.party.marathon.invalid_time", NamedTextColor.RED))
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
        val playerArgument by OfflinePlayerArgument
        suspendSyntax(playerArgument) {
            Messaging.outgoing.inviteToParty(player.uuid, get(playerArgument).uuid)
        }

    })

suspend fun handlePartyChat(message: String, player: Player) {
    val msg = if (Permissions.hasPermission(player.uuid, "chat.minimessage") != true) {
        // Escape the chat message to prevent players using MiniMessage tags in party chat messages
        miniMessage.escapeTags(message)
    } else {
        // The player is allowed to use MiniMessage
        message
    }
    Messaging.outgoing.partyChat(msg, player)
}

class PartyChatShorthandCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {

        requirePlayers()
        usage(usageString)

        val messageArgument by StringArrayArgument
        suspendSyntax(messageArgument) {
            handlePartyChat(get(messageArgument).joinToString(" "), player)
        }
    })