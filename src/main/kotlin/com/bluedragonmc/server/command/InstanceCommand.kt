package com.bluedragonmc.server.command

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.*
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component.*
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
                +translatable(
                    "command.instance.title", BRAND_COLOR_PRIMARY_2, TextDecoration.BOLD, TextDecoration.UNDERLINED
                )
                +text(" (", BRAND_COLOR_PRIMARY_2).decoration(TextDecoration.UNDERLINED, TextDecoration.State.FALSE)
                +text(MinecraftServer.getInstanceManager().instances.size, BRAND_COLOR_PRIMARY_1)
                +text(")", BRAND_COLOR_PRIMARY_2)
                +newline()
                for (instance in MinecraftServer.getInstanceManager().instances) {
                    +newline()
                    +text(instance.uniqueId.toString(), NamedTextColor.DARK_GRAY)
                        .clickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, instance.uniqueId.toString())
                    +text(" · ", NamedTextColor.GRAY)
                    +text(instance::class.simpleName.toString(), NamedTextColor.AQUA)
                    +newline()
                    +text(" → ", NamedTextColor.GRAY)
                    var isPrimaryInstance = true
                    val game = Game.findGame(instance.uniqueId) ?:
                        Game.games.find { it.ownsInstance(instance) }
                            .also { isPrimaryInstance = false }
                    if(game?.name != null) {
                        +text(game.name, if (isPrimaryInstance) NamedTextColor.YELLOW else NamedTextColor.GREEN)
                    } else {
                        +translatable("command.instance.no_game", NamedTextColor.RED)
                    }
                    +text(" · ", NamedTextColor.GRAY)
                    if(game?.mapName != null) {
                        +text(game.mapName, if (isPrimaryInstance) NamedTextColor.GOLD else NamedTextColor.DARK_GREEN)
                    } else {
                        +translatable("command.instance.no_map", NamedTextColor.RED)
                    }
                    +text(" · ", NamedTextColor.GRAY)
                    +translatable("command.instance.players", NamedTextColor.GRAY, text(instance.players.size))
                    +space()
                    val connectButtonColor =
                        if (sender is Player && player.instance != instance) NamedTextColor.YELLOW else NamedTextColor.GRAY
                    +translatable("command.instance.action.connect", connectButtonColor)
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
                player.sendMessage(formatErrorTranslated("command.instance.remove.waiting", instance.players.size))
            }
            InstanceUtils.forceUnregisterInstance(instance).thenAccept {
                Database.IO.launch {
                    Messaging.outgoing.notifyInstanceRemoved(instance.uniqueId)
                }
                player.sendMessage(formatMessageTranslated("command.instance.remove.success", instance.uniqueId))
            }
        }
    }
})
