package com.bluedragonmc.server.command

import com.bluedragonmc.messages.GameType
import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.queue.Queue
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.arguments.ArgumentWord

class JoinCommand(name: String, private val usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, null, block = {
    val gameArgument = ArgumentWord("game").from(*Queue.gameClasses.keys.toTypedArray())
    val mapArgument = ArgumentWord("map")
    usage(usageString)
    syntax(gameArgument) {
        Environment.current.queue.queue(player, GameType(get(gameArgument), null, null))
    }.requirePlayers()
    syntax(gameArgument, mapArgument) {
        Environment.current.queue.queue(player, GameType(get(gameArgument), null, get(mapArgument)))
    }.apply {
        requirePlayers()
        requirePermission("command.game.map", Component.translatable("command.join.no_map_permission", NamedTextColor.RED))
    }
})