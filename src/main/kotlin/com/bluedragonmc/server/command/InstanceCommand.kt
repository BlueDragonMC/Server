package com.bluedragonmc.server.command

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.buildComponent
import com.bluedragonmc.server.utils.clickEvent
import com.bluedragonmc.server.utils.hoverEventTranslatable
import com.bluedragonmc.server.utils.surroundWithSeparators
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player

/**
 * Usage:
 * - /instance list
 * - /instance join <Instance UUID>
 */
class InstanceCommand(name: String, usageString: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, block = {
    usage(usageString)
    subcommand("list") {
        syntax {
            val component = buildComponent {
                +Component.translatable(
                    "command.instance.title", NamedTextColor.BLUE, TextDecoration.BOLD, TextDecoration.UNDERLINED
                )
                +Component.newline()
                for (instance in MinecraftServer.getInstanceManager().instances) {
                    +Component.newline()
                    +Component.text(instance.uniqueId.toString(), NamedTextColor.DARK_GRAY)
                        .clickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, instance.uniqueId.toString())
                    +Component.text(" · ", NamedTextColor.GRAY)
                    +Component.text(instance::class.simpleName.toString(), NamedTextColor.AQUA)
                    +Component.newline()
                    +Component.text(" → ", NamedTextColor.GRAY)
                    val game = Game.findGame(instance.uniqueId)
                    if(game?.name != null) {
                        +Component.text(game.name, NamedTextColor.YELLOW)
                    } else {
                        +Component.translatable("command.instance.no_game", NamedTextColor.RED)
                    }
                    +Component.text(" · ", NamedTextColor.GRAY)
                    if(game?.mapName != null) {
                        +Component.text(game.mapName, NamedTextColor.GOLD)
                    } else {
                        +Component.translatable("command.instance.no_map", NamedTextColor.RED)
                    }
                    +Component.text(" · ", NamedTextColor.GRAY)
                    +Component.translatable("command.instance.players", NamedTextColor.GRAY, Component.text(instance.players.size))
                    +Component.space()
                    val connectButtonColor =
                        if (sender is Player && sender.instance != instance) NamedTextColor.YELLOW else NamedTextColor.GRAY
                    +Component.translatable("command.instance.action.connect", connectButtonColor)
                        .hoverEventTranslatable("command.instance.action.connect.hover", NamedTextColor.YELLOW)
                        .clickEvent("/instance join ${instance.uniqueId}")
                }
            }.surroundWithSeparators()

            sender.sendMessage(component)
        }
    }

    val instanceArgument by InstanceArgument

    subcommand("join") {
        syntax(instanceArgument) {
            val instance = get(instanceArgument)
            player.sendMessage(formatMessageTranslated("queue.sending", instance.uniqueId))
            try {
                player.setInstance(instance).whenCompleteAsync { _, throwable ->
                    // Send a generic error message
                    throwable?.let {
                        player.sendMessage(formatErrorTranslated("command.instance.join.fail.generic", instance.uniqueId))
                    }
                }
            } catch (exception: IllegalArgumentException) {
                // The player can not re-join its current instance.
                player.sendMessage(formatErrorTranslated("command.instance.join.fail"))
            }
        }.requirePlayers()
    }

    subcommand("remove") {
        syntax(instanceArgument) {
            val instance = get(instanceArgument)
            if (instance.players.isNotEmpty()) {
                player.sendMessage(formatErrorTranslated("command.instance.remove.fail"))
                return@syntax
            }
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
            Database.IO.launch {
                Messaging.outgoing.notifyInstanceRemoved(instance)
            }
            player.sendMessage(formatMessageTranslated("command.instance.remove.success", instance.uniqueId))
        }
    }
})
