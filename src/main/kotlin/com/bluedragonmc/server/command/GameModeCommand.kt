package com.bluedragonmc.server.command

import net.minestom.server.command.builder.arguments.ArgumentWord
import net.minestom.server.entity.GameMode

class GameModeCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {
    val gameModeArgument = ArgumentWord("gameMode").from("survival", "creative", "adventure", "spectator")
    val playerArgument by PlayerArgument

    usage(usageString)

    syntax {
        val gameMode = getGameModeFromCommandName(ctx.commandName) ?: return@syntax
        player.gameMode = gameMode
        sender.sendMessage(formatMessageTranslated("command.gamemode.own", get(gameModeArgument).lowercase()))
    }.requirePlayers()

    syntax {
        val gameMode = getGameModeFromCommandName(ctx.commandName) ?: return@syntax
        val player = getFirstPlayer(playerArgument)
        player.gameMode = gameMode
        sender.sendMessage(formatMessageTranslated("command.gamemode.other", player.name, gameMode.toString().lowercase()))
        player.sendMessage(formatMessageTranslated("command.gamemode.own", gameMode.toString().lowercase()))
    }

    syntax(gameModeArgument) {
        player.gameMode = GameMode.valueOf(get(gameModeArgument).uppercase())
        sender.sendMessage(formatMessageTranslated("command.gamemode.own", get(gameModeArgument).lowercase()))
    }.requirePlayers()

    syntax(gameModeArgument, playerArgument) {
        val player = getFirstPlayer(playerArgument)
        player.gameMode = GameMode.valueOf(get(gameModeArgument).uppercase())
        player.sendMessage(formatMessageTranslated("command.gamemode.own", get(gameModeArgument).lowercase()))
        sender.sendMessage(formatMessageTranslated("command.gamemode.other", player.name, get(gameModeArgument).lowercase()))
    }
}) {
    companion object {
        internal fun getGameModeFromCommandName(commandName: String) = when (commandName) {
            "gms" -> GameMode.SURVIVAL
            "gmc" -> GameMode.CREATIVE
            "gmsp" -> GameMode.SPECTATOR
            "gma" -> GameMode.ADVENTURE
            else -> null
        }
    }
}