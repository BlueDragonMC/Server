package com.bluedragonmc.server.command

import com.bluedragonmc.messages.NotifyInstanceRemovedMessage
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.utils.buildComponent
import com.bluedragonmc.server.utils.clickEvent
import com.bluedragonmc.server.utils.hoverEvent
import com.bluedragonmc.server.utils.surroundWithSeparators
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
                +Component.text(
                    "All running instances", NamedTextColor.BLUE, TextDecoration.BOLD, TextDecoration.UNDERLINED
                )
                +Component.newline()
                for (instance in MinecraftServer.getInstanceManager().instances) {
                    +Component.newline()
                    +Component.text(instance.uniqueId.toString(), NamedTextColor.DARK_GRAY)
                        .clickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, instance.uniqueId.toString())
                    +Component.text(" · ", NamedTextColor.GRAY)
                    +Component.text(instance::class.simpleName ?: "null", NamedTextColor.AQUA)
                    +Component.newline()
                    +Component.text(" → ", NamedTextColor.GRAY)
                    val game = Game.findGame(instance.uniqueId)
                    +Component.text(game?.name ?: "No game", NamedTextColor.YELLOW)
                    +Component.text(" · ", NamedTextColor.GRAY)
                    +Component.text(game?.mapName ?: "No map", NamedTextColor.GOLD)
                    +Component.text(" · ", NamedTextColor.GRAY)
                    +Component.text("${instance.players.size} online", NamedTextColor.GRAY)
                    +Component.space()
                    val connectButtonColor =
                        if (sender is Player && sender.instance != instance) NamedTextColor.YELLOW else NamedTextColor.GRAY
                    +Component.text("(Connect)", connectButtonColor)
                        .hoverEvent("Click to join the instance!", NamedTextColor.YELLOW)
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
            player.sendMessage(formatMessage("Sending you to {}...", instance.uniqueId))
            try {
                player.setInstance(instance).whenCompleteAsync { _, throwable ->
                    // Send a generic error message
                    throwable?.let {
                        player.sendMessage(formatErrorMessage("There was an error sending you to {}!", instance.uniqueId))
                    }
                }
            } catch (exception: IllegalArgumentException) {
                // The player can not re-join its current instance.
                player.sendMessage(formatErrorMessage("You are already in this instance!"))
            }
        }.requirePlayers()
    }

    subcommand("remove") {
        syntax(instanceArgument) {
            val instance = get(instanceArgument)
            if (instance.players.isNotEmpty()) {
                player.sendMessage(formatErrorMessage("Instances with players cannot be removed."))
                return@syntax
            }
            MinecraftServer.getInstanceManager().unregisterInstance(instance)
            player.sendMessage(formatMessage("Removed instance {}.", instance.uniqueId))
            MessagingModule.publish(NotifyInstanceRemovedMessage(MessagingModule.containerId, instance.uniqueId))
        }
    }
})
