package com.bluedragonmc.server.command

import net.kyori.adventure.text.Component
import net.minestom.server.command.builder.arguments.ArgumentWord
import net.minestom.server.entity.GameMode

class GameModeCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {
    val gameModeArgument = ArgumentWord("gameMode").from("survival", "creative", "adventure", "spectator")
    val playerArgument by PlayerArgument

    usage(usageString)

    syntax(gameModeArgument) {
        val gameMode = get(gameModeArgument)
        player.gameMode = GameMode.valueOf(gameMode.uppercase())
        sender.sendMessage(formatMessageTranslated("commands.gamemode.success.self", Component.translatable("gameMode.${gameMode.lowercase()}")))
    }.requirePlayers()

    syntax(gameModeArgument, playerArgument) {
        val gameMode = get(gameModeArgument)
        val player = getFirstPlayer(playerArgument)
        player.gameMode = GameMode.valueOf(gameMode.uppercase())
        sender.sendMessage(formatMessageTranslated("commands.gamemode.success.other", player.name, Component.translatable("gameMode.${gameMode.lowercase()}")))
    }
}) {

    open class SingleGameModeCommand(name: String, private val gameMode: GameMode) : BlueDragonCommand(name, permission = "command.gamemode", block = {
        val component = Component.translatable("gameMode.${gameMode.toString().lowercase()}")

        val otherArgument by PlayerArgument
        syntax {
            player.gameMode = gameMode
            player.sendMessage(formatMessageTranslated("commands.gamemode.success.self", component))
        }
        syntax(otherArgument) {
            val other = getFirstPlayer(otherArgument)
            other.gameMode = gameMode
            sender.sendMessage(formatMessageTranslated("commands.gamemode.success.other", other.name, component))
        }
    })

    class GameModeSurvivalCommand : SingleGameModeCommand("gms", GameMode.SURVIVAL)
    class GameModeCreativeCommand : SingleGameModeCommand("gmc", GameMode.CREATIVE)
    class GameModeAdventureCommand : SingleGameModeCommand("gma", GameMode.ADVENTURE)
    class GameModeSpectatorCommand : SingleGameModeCommand("gmsp", GameMode.SPECTATOR)
}