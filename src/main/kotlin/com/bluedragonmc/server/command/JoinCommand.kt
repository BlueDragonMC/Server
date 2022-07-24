package com.bluedragonmc.server.command

import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.queue
import net.minestom.server.command.builder.arguments.ArgumentWord

class JoinCommand(name: String, private val usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, null, block = {
    val gameArgument = ArgumentWord("game").from(*queue.gameClasses.keys.toTypedArray())
    val mapArgument = ArgumentWord("map")
    usage(usageString)
    syntax(gameArgument) {
        queue.queue(player, GameType(get(gameArgument), null, null))
    }.requirePlayers()
    syntax(gameArgument, mapArgument) {
        queue.queue(player, GameType(get(gameArgument), null, get(mapArgument)))
    }.apply {
        requirePlayers()
        requirePermission("command.game.map", "Only donators can queue for a specific map.")
    }
})