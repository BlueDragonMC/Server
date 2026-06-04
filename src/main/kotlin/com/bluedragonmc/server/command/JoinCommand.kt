package com.bluedragonmc.server.command

import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.server.api.Environment
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.arguments.ArgumentWord

class JoinCommand(name: String, private val usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    val gameArgument = ArgumentWord("game").from(*Environment.gameClasses.toTypedArray())
    val modeArgument by WordArgument
    val mapArgument by WordArgument

    usage(usageString)

    syntax(gameArgument) {
        Environment.queue.queue(player, gameType {
            this.name = get(gameArgument)
        })
    }.requirePlayers()

    syntax(gameArgument, modeArgument) {
        Environment.queue.queue(player, gameType {
            this.name = get(gameArgument)
            this.mode = get(modeArgument)
        })
    }.apply {
        requirePlayers()
        requirePermission("command.join.mode", Component.translatable("command.join.no_mode_permission", NamedTextColor.RED))
    }

    syntax(gameArgument, modeArgument, mapArgument) {
        Environment.queue.queue(player, gameType {
            this.name = get(gameArgument)
            this.mode = get(modeArgument)
            this.mapId = get(mapArgument)
        })
    }.apply {
        requirePlayers()
        requirePermission("command.join.map", Component.translatable("command.join.no_map_permission", NamedTextColor.RED))
    }
})