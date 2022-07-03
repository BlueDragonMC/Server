package com.bluedragonmc.server.command

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.TitlePart
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import java.time.Duration

class GameCommand(name: String, usage: String, vararg aliases: String?) : BlueDragonCommand(name, usage, *aliases) {
    init {
        addSubcommand(EndCommand)
        addSubcommand(StartCommand)
    }

    /**
     * Ends the current game
     */
    object EndCommand : Command("end") {
        init {
            setDefaultExecutor { sender, context ->
                sender as Player
                val game = Game.findGame(sender)
                game!!.endGame(Duration.ZERO)
                sender.sendMessage(Component.text("Game ended successfully.", NamedTextColor.GREEN))
            }
        }
    }

    object StartCommand : Command("start") {
        init {
            setDefaultExecutor { sender, _ ->
                sender as Player
                val game = Game.findGame(sender)
                game!!.sendTitlePart(TitlePart.TITLE, Component.text("GO!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                game.sendTitlePart(TitlePart.SUBTITLE, Component.text("Game started by an admin.", NamedTextColor.GREEN).decorate(TextDecoration.ITALIC))
                game.callEvent(GameStartEvent(game))
            }
        }
    }
}