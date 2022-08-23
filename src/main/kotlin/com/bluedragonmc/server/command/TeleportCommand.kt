package com.bluedragonmc.server.command

/**
 * Usage:
 * /tp <player>
 * /tp <x> <y> <z>
 * /tp <player> <other>
 */
class TeleportCommand(name: String, usageString: String, vararg aliases: String?) :
    BlueDragonCommand(name, aliases, block = {
        val coordsArgument by BlockPosArgument
        val playerArgument by PlayerArgument
        val player2Argument by PlayerArgument

        usage(usageString)

        syntax(coordsArgument) {
            val pos = get(coordsArgument).fromSender(sender).asPosition()
            player.teleport(pos)
            sender.sendMessage(formatMessageTranslated("command.teleport.self", formatPos(pos)))
        }.requirePlayers()

        syntax(playerArgument) {
            val other = getFirstPlayer(playerArgument)
            player.teleport(other.position)
            sender.sendMessage(formatMessageTranslated("command.teleport.self", other.name))
        }.requirePlayers()

        syntax(playerArgument, coordsArgument) {
            val other = getFirstPlayer(playerArgument)
            val pos = get(coordsArgument).fromSender(sender).asPosition()
            other.teleport(pos)
            sender.sendMessage(formatMessageTranslated("command.teleport.other", other.name, formatPos(pos)))
        }

        syntax(playerArgument, player2Argument) {
            val other1 = getFirstPlayer(playerArgument)
            val other2 = getFirstPlayer(player2Argument)
            other1.teleport(other2.position)
            sender.sendMessage(formatMessageTranslated("command.teleport.other", other1.name, other2.name))
        }
    })