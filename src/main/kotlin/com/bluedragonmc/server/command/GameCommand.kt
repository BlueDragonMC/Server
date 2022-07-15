package com.bluedragonmc.server.command

import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.gameplay.TeamModule
import com.bluedragonmc.server.module.minigame.WinModule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import java.time.Duration

/**
 * Usage:
 * /game <start|end>
 */
class GameCommand(name: String, usageString: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, {
    requirePlayers()
    usage(usageString)

    subcommand("end") {
        syntax {
            game.endGame(Duration.ZERO)
            game.callEvent(WinModule.WinnerDeclaredEvent(game, TeamModule.Team()))
            sender.sendMessage(formatMessage("Game ended successfully."))
        }.requireInGame()
    }

    subcommand("start") {
        syntax {
            game.showTitle(
                Title.title(
                    Component.text("GO!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    Component.text("Game started by an admin.", NamedTextColor.GREEN).decorate(TextDecoration.ITALIC)
                )
            )
            game.callEvent(GameStartEvent(game))
            sender.sendMessage(formatMessage("Game started successfully."))
        }.requireInGame()
    }
})