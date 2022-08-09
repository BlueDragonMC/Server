package com.bluedragonmc.server.command

class FlyCommand(name: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    val playerArgument by PlayerArgument

    syntax {
        player.isAllowFlying = !player.isFlying
        player.isFlying = player.isAllowFlying
        if (player.isFlying) player.sendMessage(formatMessageTranslated("command.fly.own.flying"))
        else player.sendMessage(formatMessageTranslated("command.fly.own.not_flying"))
    }.requirePlayers()

    syntax(playerArgument) {
        val player = getFirstPlayer(playerArgument)
        player.isAllowFlying = !player.isFlying
        player.isFlying = player.isAllowFlying
        if (player.isFlying) player.sendMessage(formatMessageTranslated("command.fly.other.flying", player.name))
        else player.sendMessage(formatMessageTranslated("command.fly.other.not_flying", player.name))
    }
})