package com.bluedragonmc.server.command

import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import java.time.Duration

/**
 * Usage:
 * /game <start|end>
 */
class GameCommand(name: String, usageString: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, block = {
    requirePlayers()
    usage(usageString)

    subcommand("end") {
        syntax {
            game.endGame(Duration.ZERO)
            game.callEvent(WinModule.WinnerDeclaredEvent(game, TeamModule.Team()))
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
                sender.sendMessage(formatMessage("All modules ({}):", game.modules.size))
                for (module in game.modules) {
                    sender.sendMessage(formatMessage("- {}", module.javaClass.simpleName).hoverEvent(HoverEvent.showText(module.toString() withColor NamedTextColor.GRAY)))
                }
            }.requireInGame()
        }
        subcommand("unload") {
            val moduleArgument by WordArgument
            syntax(moduleArgument) {
                val modToRemove = get(moduleArgument)
                val unremovableModules = listOf("DatabaseModule", "MessagingModule")
                for (module in game.modules) {
                    if (module.javaClass.simpleName == modToRemove) {
                        if (unremovableModules.contains(modToRemove) || module is InstanceModule) {
                            sender.sendMessage(formatErrorMessage("This module cannot be unloaded."))
                            return@syntax
                        }
                        game.unregister(module)
                        sender.sendMessage(formatMessage("{} was successfully unloaded.", module.javaClass.simpleName))
                        return@syntax
                    }
                }
                sender.sendMessage(formatErrorMessage("Module not found."))
            }
        }
    }
})