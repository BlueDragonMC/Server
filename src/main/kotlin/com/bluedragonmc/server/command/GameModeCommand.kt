package com.bluedragonmc.server.command

import net.minestom.server.command.builder.arguments.ArgumentWord
import net.minestom.server.entity.GameMode

class GameModeCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {
    val gameModeArgument = ArgumentWord("gameMode").from("survival", "creative", "adventure", "spectator")
    val playerArgument by PlayerArgument

    usage(usageString)

    syntax {
        val gameMode = when (ctx.commandName) {
            "gms" -> GameMode.SURVIVAL
            "gmc" -> GameMode.CREATIVE
            "gmsp" -> GameMode.SPECTATOR
            "gma" -> GameMode.ADVENTURE
            else -> {
                sender.sendMessage(formatMessage("Your game mode is currently {}.", player.gameMode.toString().lowercase()))
                return@syntax
            }
        }
        player.gameMode = gameMode
        sender.sendMessage(formatMessage("Your game mode has been updated to {}.", get(gameModeArgument).lowercase()))
    }.requirePlayers()

    syntax {
        val gameMode = when (ctx.commandName) {
            "gms" -> GameMode.SURVIVAL
            "gmc" -> GameMode.CREATIVE
            "gmsp" -> GameMode.SPECTATOR
            "gma" -> GameMode.ADVENTURE
            else -> return@syntax
        }
        val player = getFirstPlayer(playerArgument)
        player.gameMode = gameMode
        sender.sendMessage(formatMessage("{}'s game mode has been updated to {}.", player.name, gameMode.toString().lowercase()))
        player.sendMessage(formatMessage("Your game mode has been updated to {}.", gameMode.toString().lowercase()))
    }

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