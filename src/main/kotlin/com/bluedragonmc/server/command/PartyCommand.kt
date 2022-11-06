package com.bluedragonmc.server.command

import com.bluedragonmc.api.grpc.*
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.module.database.Permissions
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.utils.miniMessage
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component

class PartyCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, null, block = {
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
            MessagingModule.Stubs.partyStub.inviteToParty(partyInviteRequest {
                partyOwnerUuid = player.uuid.toString()
                playerUuid = getFirstPlayer(playerArgument).uuid.toString()
            })
        }
    }

    subcommand("kick") {
        val playerArgument by PlayerArgument
        suspendSyntax(playerArgument) {
            MessagingModule.Stubs.partyStub.removeFromParty(partyRemoveRequest {
                partyOwnerUuid = player.uuid.toString()
                playerUuid = getFirstPlayer(playerArgument).uuid.toString()
            })
        }
    }

    subcommand("chat") {
        val messageArgument by StringArrayArgument
        suspendSyntax(messageArgument) {
            val msg = if(!Permissions.hasPermission((player as CustomPlayer).data, "chat.minimessage")) {
                // Escape the chat message to prevent players using MiniMessage tags in party chat messages
                miniMessage.escapeTags(get(messageArgument).joinToString(" "))
            } else {
                // The player is allowed to use MiniMessage
                get(messageArgument).joinToString(" ")
            }
            MessagingModule.Stubs.partyStub.partyChat(partyChatRequest {
                playerUuid = player.uuid.toString()
                message = msg
            })
        }
    }

    subcommand("accept") {
        val playerArgument by PlayerArgument
        suspendSyntax(playerArgument) {
            MessagingModule.Stubs.partyStub.acceptInvitation(partyAcceptInviteRequest {
                partyOwnerUuid = player.uuid.toString()
                playerUuid = getFirstPlayer(playerArgument).uuid.toString()
            })
        }
    }

    subcommand("warp") {
        suspendSyntax {
            MessagingModule.Stubs.partyStub.warpParty(partyWarpRequest {
                partyOwnerUuid = player.uuid.toString()
                serverName = MessagingModule.serverName
                instanceUuid = player.instance!!.uniqueId.toString()
            })
        }
    }

    subcommand("transfer") {
        val playerArgument by PlayerArgument
        suspendSyntax(playerArgument) {
            MessagingModule.Stubs.partyStub.transferParty(partyTransferRequest {
                newOwnerUuid = getFirstPlayer(playerArgument).uuid.toString()
                playerUuid = player.uuid.toString()
            })
        }
    }

    subcommand("list") {
        suspendSyntax {
            val response = MessagingModule.Stubs.partyStub.partyList(partyListRequest {
                playerUuid = player.uuid.toString()
            })
            val leader = response.playersList.find { it.role == "Leader" }
            val leaderText = Component.translatable("puffin.party.list.leader", Component.text())
            val members = response.playersList.filter { it != leader}
            val membersText = formatMessageTranslated("puffin.party.list.members", members.size, *members.map { it.username }.toTypedArray())
            sender.sendMessage(leaderText + Component.newline() + membersText)
        }
    }

})