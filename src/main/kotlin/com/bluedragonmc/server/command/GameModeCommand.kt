package com.bluedragonmc.server.command

import net.minestom.server.command.builder.arguments.ArgumentWord
import net.minestom.server.entity.GameMode

class GameModeCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, {
    val gameModeArgument = ArgumentWord("gameMode").from("survival", "creative", "adventure", "spectator")
    val playerArgument by PlayerArgument

    usage(usageString)

    syntax(gameModeArgument) {
        player.gameMode = GameMode.valueOf(get(gameModeArgument).uppercase())
        sender.sendMessage(formatMessage("Your game mode has been updated to {}.", get(gameModeArgument).lowercase()))
    }.requirePlayers()

    syntax(gameModeArgument, playerArgument) {
        val player = getFirstPlayer(playerArgument)
        player.gameMode = GameMode.valueOf(get(gameModeArgument).uppercase())
        player.sendMessage(formatMessage("Your game mode has been updated to {}.", get(gameModeArgument).lowercase()))
        sender.sendMessage(
            formatMessage(
                "{}'s game mode has been updated to {}.", player.name, get(gameModeArgument).lowercase()
            )
        )
    }
})