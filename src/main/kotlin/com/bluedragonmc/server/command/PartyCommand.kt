package com.bluedragonmc.server.command

import com.bluedragonmc.messages.*
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.module.database.Permissions
import com.bluedragonmc.server.module.messaging.MessagingModule
import net.kyori.adventure.text.minimessage.MiniMessage

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
        syntax(playerArgument) {
            MessagingModule.publish(InvitePlayerToPartyMessage(player.uuid, getFirstPlayer(playerArgument).uuid))
        }
    }

    subcommand("kick") {
        val playerArgument by PlayerArgument
        syntax(playerArgument) {
            MessagingModule.publish(RemovePlayerFromPartyMessage(player.uuid, getFirstPlayer(playerArgument).uuid))
        }
    }

    subcommand("chat") {
        val chatArgument by StringArrayArgument
        syntax(chatArgument) {
            val msg = if(!Permissions.hasPermission((player as CustomPlayer).data, "chat.minimessage")) {
                // Escape the chat message to prevent players using MiniMessage tags in party chat messages
                MiniMessage.miniMessage().escapeTags(get(chatArgument).joinToString(" "))
            } else {
                // The player is allowed to use MiniMessage
                get(chatArgument).joinToString(" ")
            }
            MessagingModule.publish(PartyChatMessage(player.uuid, msg))
        }
    }

    subcommand("accept") {
        val playerArgument by PlayerArgument
        syntax(playerArgument) {
            MessagingModule.publish(AcceptPartyInvitationMessage(getFirstPlayer(playerArgument).uuid, player.uuid))
        }
    }

    subcommand("warp") {
        syntax {
            MessagingModule.publish(PartyWarpMessage(player.uuid, MessagingModule.containerId, player.instance!!.uniqueId))
        }
    }

    subcommand("transfer") {
        val playerArgument by PlayerArgument
        syntax(playerArgument) {
            MessagingModule.publish(PartyTransferMessage(player.uuid, getFirstPlayer(playerArgument).uuid))
        }
    }

    subcommand("list") {
        syntax {
            MessagingModule.publish(PartyListMessage(player.uuid))
        }
    }

})