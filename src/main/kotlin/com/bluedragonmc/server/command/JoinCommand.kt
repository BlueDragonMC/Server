package com.bluedragonmc.server.command

import com.bluedragonmc.api.grpc.CommonTypes.GameType.GameTypeFieldSelector
import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.server.Environment
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.arguments.ArgumentWord

class JoinCommand(name: String, private val usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, null, block = {
    val gameArgument = ArgumentWord("game").from(*Environment.current.gameClasses.toTypedArray())
    val mapArgument = ArgumentWord("map")
    usage(usageString)
    syntax(gameArgument) {
        Environment.current.queue.queue(player, gameType {
            this.name = get(gameArgument)
            selectors += GameTypeFieldSelector.GAME_NAME
        })
    }.requirePlayers()
    syntax(gameArgument, mapArgument) {
        Environment.current.queue.queue(player, gameType {
            this.name = get(gameArgument)
            this.mapName = get(mapArgument)
            selectors += GameTypeFieldSelector.GAME_NAME
            selectors += GameTypeFieldSelector.MAP_NAME
        })
    }.apply {
        requirePlayers()
        requirePermission("command.game.map", Component.translatable("command.join.no_map_permission", NamedTextColor.RED))
    }
})