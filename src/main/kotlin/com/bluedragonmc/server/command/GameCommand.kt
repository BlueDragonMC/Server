package com.bluedragonmc.server.command

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title

/**
 * Usage:
 * /game <start|end>
 */
class GameCommand(name: String, usageString: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, block = {
    requirePlayers()
    usage(usageString)

    subcommand("end") {
        syntax {
            if (game.state == GameState.INGAME) {
                game.callEvent(WinModule.WinnerDeclaredEvent(game, TeamModule.Team()))
            }
            game.endGame()
            sender.sendMessage(formatMessageTranslated("command.game.ended"))
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
            sender.sendMessage(formatMessageTranslated("command.game.started"))
        }.requireInGame()
    }

    subcommand("module") {
        subcommand("list") {
            syntax {
                sender.sendMessage(formatMessageTranslated("command.game.module.list", game.modules.size))
                for (module in game.modules) {
                    sender.sendMessage(
                        Component.text(module.javaClass.simpleName, BRAND_COLOR_PRIMARY_1)
                            .hoverEvent(HoverEvent.showText(module.toString() withColor NamedTextColor.GRAY))
                    )
                }
            }.requireInGame()
        }
        subcommand("unload") {
            val moduleArgument by WordArgument
            syntax(moduleArgument) {
                val modToRemove = get(moduleArgument)
                for (module in game.modules) {
                    if (module.javaClass.simpleName == modToRemove) {
                        game.unregister(module)
                        sender.sendMessage(formatMessageTranslated("command.game.module.unloaded", module.javaClass.simpleName))
                        return@syntax
                    }
                }
                sender.sendMessage(formatErrorTranslated("command.game.module.not_found"))
            }
        }
    }
})