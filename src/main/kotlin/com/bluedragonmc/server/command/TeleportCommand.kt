package com.bluedragonmc.server.command

import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * Usage:
 * /tp <player>
 * /tp <x> <y> <z>
 * /tp <player> <other>
 */
class TeleportCommand(name: String, usageString: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, {
    val coordsArgument by BlockPosArgument
    val playerArgument by PlayerArgument
    val player2Argument by PlayerArgument

    usage(usageString)

    syntax(coordsArgument) {
        val pos = get(coordsArgument).fromSender(sender).asPosition()
        player.teleport(pos)
        sender.sendMessage(
            Component.text(
                "You were teleported to (${pos.x}, ${pos.y}, ${pos.z}).", NamedTextColor.GREEN
            )
        )
    }.requirePlayers()

    syntax(playerArgument) {
        val other = getFirstPlayer(playerArgument)
        player.teleport(other.position)
        sender.sendMessage(
            Component.text("You were teleported to ", NamedTextColor.GREEN) + other.name + Component.text(
                ".", NamedTextColor.GREEN
            )
        )
    }.requirePlayers()

    syntax(playerArgument, coordsArgument) {
        val other = getFirstPlayer(playerArgument)
        val pos = get(coordsArgument).fromSender(sender).asPosition()
        other.teleport(pos)
        sender.sendMessage(
            other.name + Component.text(
                " was teleported to (${pos.x}, ${pos.y}, ${pos.z}).", NamedTextColor.GREEN
            )
        )
    }

    syntax(playerArgument, player2Argument) {
        val other1 = getFirstPlayer(playerArgument)
        val other2 = getFirstPlayer(player2Argument)
        other1.teleport(other2.position)
        sender.sendMessage(
            other1.name + Component.text(
                " was teleported to ", NamedTextColor.GREEN
            ) + other2.name + Component.text(".")
        )
    }
})