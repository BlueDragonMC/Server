package com.bluedragonmc.server.command

import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.arguments.ArgumentWord
import net.minestom.server.entity.GameMode

class GameModeCommand(name: String, usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, {
    val gameModeArgument = ArgumentWord("gameMode").from("survival", "creative", "adventure", "spectator")
    val playerArgument by PlayerArgument

    usage(usageString)

    syntax(gameModeArgument) {
        player.gameMode = GameMode.valueOf(get(gameModeArgument).uppercase())
        sender.sendMessage(
            Component.text(
                "Your game mode has been updated to ${get(gameModeArgument).lowercase()}.", NamedTextColor.GREEN
            )
        )
    }.requirePlayers()

    syntax(gameModeArgument, playerArgument) {
        val player = getFirstPlayer(playerArgument)
        player.gameMode = GameMode.valueOf(get(gameModeArgument).uppercase())
        player.sendMessage(
            Component.text(
                "Your game mode has been updated to ${get(gameModeArgument).lowercase()}.", NamedTextColor.GREEN
            )
        )
        sender.sendMessage(
            player.name + Component.text(
                "'s game mode has been updated to ${
                    player.gameMode.toString().lowercase()
                }.", NamedTextColor.GREEN
            )
        )
    }
})