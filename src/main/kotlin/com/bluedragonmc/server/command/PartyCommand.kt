package com.bluedragonmc.server.command

import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.service.Permissions
import com.bluedragonmc.server.utils.miniMessage
import com.bluedragonmc.server.utils.plus
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component

class PartyCommand(name: String, usageString: String, vararg aliases: String) :
    BlueDragonCommand(name, aliases, block = {
        requirePlayers() // All subcommands require a player
        usage(usageString)
        /*
        invite <player>
        kick <player>
        promote <player>
        warp
        chat <message>
        list
         */

        subcommand("invite") {
            val playerArgument by PlayerArgument
            suspendSyntax(playerArgument) {
                Messaging.outgoing.inviteToParty(player.uuid, getFirstPlayer(playerArgument).uuid)
            }
        }

        subcommand("kick") {
            val playerArgument by PlayerArgument
            suspendSyntax(playerArgument) {
                Messaging.outgoing.kickFromParty(player.uuid, getFirstPlayer(playerArgument).uuid)
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
                val msg = if (Permissions.hasPermission(player.uuid, "chat.minimessage") != true) {
                    // Escape the chat message to prevent players using MiniMessage tags in party chat messages
                    miniMessage.escapeTags(get(messageArgument).joinToString(" "))
                } else {
                    // The player is allowed to use MiniMessage
                    get(messageArgument).joinToString(" ")
                }
                Messaging.outgoing.partyChat(msg, player)
            }
        }

        subcommand("accept") {
            val playerArgument by PlayerArgument
            suspendSyntax(playerArgument) {
                Messaging.outgoing.acceptPartyInvitation(getFirstPlayer(playerArgument).uuid, player.uuid)
            }
        }

        subcommand("warp") {
            suspendSyntax {
                Messaging.outgoing.warpParty(player, player.instance!!)
            }
        }

        subcommand("transfer") {
            val playerArgument by PlayerArgument
            suspendSyntax(playerArgument) {
                Messaging.outgoing.transferParty(player, getFirstPlayer(playerArgument).uuid)
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
                }
            }
        }

    })