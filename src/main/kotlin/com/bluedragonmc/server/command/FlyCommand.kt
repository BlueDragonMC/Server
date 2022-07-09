package com.bluedragonmc.server.command

class FlyCommand(name: String, vararg aliases: String) : BlueDragonCommand(name, aliases, {
    val playerArgument by PlayerArgument

    syntax {
        player.isFlying = !player.isFlying
    }.requirePlayers()

    syntax(playerArgument) {
        val player = getFirstPlayer(playerArgument)
        player.isFlying = !player.isFlying
    }
})