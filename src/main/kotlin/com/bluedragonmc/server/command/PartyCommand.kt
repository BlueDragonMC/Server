package com.bluedragonmc.server.command

import com.bluedragonmc.messages.*
import com.bluedragonmc.server.module.messaging.MessagingModule
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.minestom.server.command.builder.arguments.ArgumentStringArray

class PartyCommand(name: String, usage: String, vararg aliases: String) : BlueDragonCommand(name, aliases, {
    requirePlayers()
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
            val component = MiniMessage.miniMessage().deserialize("<msg>", Placeholder.unparsed("msg", get(chatArgument).joinToString(separator = " ")))
            MessagingModule.publish(PartyChatMessage(player.uuid, MiniMessage.miniMessage().serialize(component)))
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

})