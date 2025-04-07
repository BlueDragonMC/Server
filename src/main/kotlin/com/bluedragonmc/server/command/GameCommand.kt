package com.bluedragonmc.server.command

import com.bluedragonmc.api.grpc.CommonTypes.GameType
import com.bluedragonmc.api.grpc.GsClient
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.module.minigame.WinModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.buildComponent
import com.bluedragonmc.server.utils.surroundWithSeparators
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.command.CommandSender
import java.util.*

/**
 * Usage:
 * /game <start|end>
 */
class GameCommand(name: String, usageString: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, block = {
    requirePlayers()
    usage(usageString)

    subcommand("end") {

        fun end(sender: CommandSender, game: Game) {
            if (game.state == GameState.INGAME) {
                game.callEvent(WinModule.WinnerDeclaredEvent(game, TeamModule.Team()))
            }
            game.endGame()
            sender.sendMessage(formatMessageTranslated("command.game.ended"))
        }

        syntax {
            end(sender, game)
        }.requireInGame()

        val gameArgument by GameArgument

        syntax(gameArgument) {
            end(sender, get(gameArgument))
        }
    }

    subcommand("start") {

        fun start(sender: CommandSender, game: Game) {
            if (game.state == GameState.INGAME) {
                sender.sendMessage(formatErrorTranslated("command.game.start.already_started"))
                return
            }
            game.showTitle(
                Title.title(
                    text("GO!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    text("Game started by an admin.", NamedTextColor.GREEN).decorate(TextDecoration.ITALIC)
                )
            )
            game.callEvent(GameStartEvent(game))
            sender.sendMessage(formatMessageTranslated("command.game.started"))
        }

        syntax {
            start(sender, game)
        }.requireInGame()

        val gameArgument by GameArgument
        syntax(gameArgument) {
            start(sender, get(gameArgument))
        }
    }

    subcommand("list") {
        syntax {
            val components = Game.games.map {
                buildComponent {
                    +text(it.id, BRAND_COLOR_PRIMARY_1)
                    +text(" · ", NamedTextColor.GRAY)
                    +text(it.name, BRAND_COLOR_PRIMARY_1)
                    +text(" · ", NamedTextColor.GRAY)
                    +text(it.mapName, BRAND_COLOR_PRIMARY_1)
                    +text(" · ", NamedTextColor.GRAY)
                    +text(it.players.size, BRAND_COLOR_PRIMARY_1)
                        .hoverEvent(text(it.players.joinToString { it.username }))
                    +text("/", NamedTextColor.DARK_GRAY)
                    +text(it.maxPlayers, BRAND_COLOR_PRIMARY_1)
                }
            }
            val msg = Component.join(JoinConfiguration.newlines(), components)
                .surroundWithSeparators()
            sender.sendMessage(msg)
        }
    }

    subcommand("join") {
        val gameArgument by GameArgument
        syntax(gameArgument) {
            val game = get(gameArgument)
            game.addPlayer(player, sendPlayer = true)
        }
    }

    subcommand("module") {
        subcommand("list") {
            syntax {
                sender.sendMessage(formatMessageTranslated("command.game.module.list", game.modules.size))
                for (module in game.modules) {
                    sender.sendMessage(
                        text(module.javaClass.simpleName, BRAND_COLOR_PRIMARY_1)
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

    subcommand("create") {
        val gameType by WordArgument
        val mapName by StringArgument
        val modeArgument by StringArgument

        fun create(sender: CommandSender, type: String, map: String, mode: String) {
            val newGame = Environment.queue.createInstance(
                GsClient.CreateInstanceRequest.newBuilder()
                    .setGameType(GameType.newBuilder()
                        .setName(type)
                        .setMapName(map)
                        .setMode(mode)).setCorrelationId(UUID.randomUUID().toString()).build()
            )

            if (newGame != null) {
                sender.sendMessage(formatMessageTranslated("command.game.create.success", newGame.id))
            } else {
                sender.sendMessage(formatErrorTranslated("command.game.create.failed"))
            }
        }

        syntax(gameType, mapName) {
            create(sender, get(gameType), get(mapName), "")
        }

        syntax(gameType, mapName, modeArgument) {
            create(sender, get(gameType), get(mapName), get(modeArgument))
        }
    }
})