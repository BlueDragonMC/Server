package com.bluedragonmc.server.command

class FlyCommand(name: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    val playerArgument by PlayerArgument

    syntax {
        player.isAllowFlying = !player.isFlying
        player.isFlying = player.isAllowFlying
        player.sendMessage(formatMessage("You are now {}.", if(player.isFlying) "flying" else "not flying"))
    }.requirePlayers()

    syntax(playerArgument) {
        val player = getFirstPlayer(playerArgument)
        player.isAllowFlying = !player.isFlying
        player.isFlying = player.isAllowFlying
        player.sendMessage(formatMessage("{} is now {}.", player.name, if(player.isFlying) "flying" else "not flying"))
    }
})