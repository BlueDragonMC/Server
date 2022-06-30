package com.bluedragonmc.server.command

import com.bluedragonmc.server.utils.asTextComponent
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

/**
 * Usage:
 * - /instance list
 * - /instance join <Instance UUID>
 */
class InstanceCommand(name: String, usage: String, vararg aliases: String?) : BlueDragonCommand(name, usage, *aliases) {
    init {
        addSubcommand(InstanceListCommand)
        addSubcommand(InstanceJoinCommand)
    }

    object InstanceListCommand : Command("list", "l") {
        init {
            setDefaultExecutor { sender, _ ->

                sender.sendMessage(
                    ("<blue><b><u>All running instances</u></b>:\n" + MinecraftServer.getInstanceManager().instances.joinToString(
                        separator = ""
                    ) {
                        // Instance type
                        "\n<aqua>${it::class.simpleName} " +
                                // Instance UUID
                                "<dark_gray><u>${it.uniqueId}</u>\n" +
                                // Online players
                                "<gray>- <blue>${it.players.size} players online " +
                                // Connect button
                                (if (sender is Player && sender.instance != it) "<yellow>" else "<gray>") + "<click:run_command:/instance join ${it.uniqueId}><hover:show_text:'<yellow>Click to join the instance!'>(Connect)</hover></click>"
                    }).asTextComponent().surroundWithSeparators()
                )
            }
        }
    }

    object InstanceJoinCommand : Command("join", "j") {
        init {
            val instanceArg = ArgumentType.UUID("instance")
            addSyntax({ sender, context ->
                val instanceId = context.get(instanceArg)
                if (sender is Player) {
                    val instance = MinecraftServer.getInstanceManager().getInstance(instanceId)
                    if (instance != null) {
                        sender.sendMessage(Component.text("Sending you to $instanceId...", NamedTextColor.YELLOW))
                        try {
                            sender.setInstance(instance).whenCompleteAsync { _, throwable ->
                                // Send a generic error message
                                throwable?.let {
                                    sender.sendMessage(
                                        Component.text(
                                            "There was an error sending you to $instanceId!", NamedTextColor.RED
                                        )
                                    )
                                }
                            }
                        } catch (exception: IllegalArgumentException) {
                            // The player can not re-join its current instance.
                            sender.sendMessage(Component.text("You are already in this instance!", NamedTextColor.RED))
                        }
                    } else sender.sendMessage(
                        Component.text("That instance does not exist!", NamedTextColor.RED)
                            .append(Component.text("($instanceId)", NamedTextColor.DARK_GRAY))
                    )
                }
            }, instanceArg)
        }
    }
}
