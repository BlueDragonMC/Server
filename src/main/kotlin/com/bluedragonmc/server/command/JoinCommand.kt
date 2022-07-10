package com.bluedragonmc.server.command

import com.bluedragonmc.server.queue
import net.minestom.server.command.builder.arguments.ArgumentWord

class JoinCommand(name: String, private val usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, {
    val gameArgument = ArgumentWord("game").from(*queue.gameClasses.keys.toTypedArray())
    usage(usageString)
    syntax(gameArgument) {
        queue.queue(player, get(gameArgument))
    }.requirePlayers()
})