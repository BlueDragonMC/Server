package com.bluedragonmc.server.command

/**
 * Usage:
 * /tp <player>
 * /tp <x> <y> <z>
 * /tp <player> <other>
 */
class TeleportCommand(name: String, usageString: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, block = {
    val coordsArgument by BlockPosArgument
    val playerArgument by PlayerArgument
    val player2Argument by PlayerArgument

    usage(usageString)

    syntax(coordsArgument) {
        val pos = get(coordsArgument).fromSender(sender).asPosition()
        player.teleport(pos)
        sender.sendMessage(formatMessage("{} were teleported to {}.", "You", formatPos(pos)))
    }.requirePlayers()

    syntax(playerArgument) {
        val other = getFirstPlayer(playerArgument)
        player.teleport(other.position)
        sender.sendMessage(formatMessage("{} were teleported to {}.", "You", other.name))
    }.requirePlayers()

    syntax(playerArgument, coordsArgument) {
        val other = getFirstPlayer(playerArgument)
        val pos = get(coordsArgument).fromSender(sender).asPosition()
        other.teleport(pos)
        sender.sendMessage(formatMessage("{} was teleported to {}.", other.name, formatPos(pos)))
    }

    syntax(playerArgument, player2Argument) {
        val other1 = getFirstPlayer(playerArgument)
        val other2 = getFirstPlayer(player2Argument)
        other1.teleport(other2.position)
        sender.sendMessage(formatMessage("{} was teleported to {}.", other1.name, other2.name))
    }
})