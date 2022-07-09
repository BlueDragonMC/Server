package com.bluedragonmc.server.command

import com.bluedragonmc.server.Game
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.CommandSender
import net.minestom.server.command.ConsoleSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.arguments.Argument
import net.minestom.server.entity.Player
import net.minestom.server.utils.entity.EntityFinder

/**
 * A basic command class that is extended by BlueDragon commands.
 */
open class BlueDragonCommand(
    name: String,
    aliases: Array<out String?> = emptyArray(),
    block: BlueDragonCommand.() -> Unit,
) : Command(name, *aliases), ConditionHolder {

    override val conditions: MutableList<ConditionCtx.() -> Boolean> = mutableListOf()

    init {
        block()
    }

    fun usage(block: CommandCtx.() -> Unit) = setDefaultExecutor { sender, context ->
        block(CommandCtx(sender, context))
    }

    fun usage(usageString: String) = usage {
        sender.sendMessage(Component.text("Usage: $usageString").color(NamedTextColor.RED))
    }

    fun subcommand(name: String, block: BlueDragonCommand.() -> Unit) =
        addSubcommand(BlueDragonCommand(name, emptyArray(), block))

    fun syntax(vararg args: Argument<*>, block: CommandCtx.() -> Unit) = Syntax(this, args.toList(), block)

    data class CommandCtx(val sender: CommandSender, val ctx: CommandContext) {
        val player by lazy { sender as Player }
        val game by lazy { Game.findGame(player)!! }

        fun <T> get(argument: Argument<T>): T = ctx.get(argument)
        fun getFirstPlayer(argument: Argument<EntityFinder>): Player =
            ctx.get(argument).findFirstPlayer(sender) ?: run {
                sender.sendMessage(Component.text("That player was not found!", NamedTextColor.RED))
                throw SilentCommandException("No player found")
            }
    }

    data class ConditionCtx(val sender: CommandSender, val ctx: CommandContext)

    data class Syntax(val parent: Command, val args: List<Argument<*>>, val handler: CommandCtx.() -> Unit) :
        ConditionHolder {
        override val conditions: MutableList<ConditionCtx.() -> Boolean> = mutableListOf()

        init {
            parent.addSyntax({ sender, context ->
                try {
                    if (!conditionsPass(ConditionCtx(sender, context))) return@addSyntax
                    handler(CommandCtx(sender, context))
                } catch (e: Throwable) {
                    if(e is SilentCommandException) return@addSyntax
                    e.printStackTrace()
                    sender.sendMessage(
                        Component.text(
                            "There was an internal error executing this command.", NamedTextColor.RED
                        )
                    )
                }
            }, *args.toTypedArray())
        }
    }

    class SilentCommandException(message: String?) : RuntimeException(message)
}

interface ConditionHolder {
    val conditions: MutableList<BlueDragonCommand.ConditionCtx.() -> Boolean>

    fun conditionsPass(context: BlueDragonCommand.ConditionCtx) = conditions.all { it(context) }

    fun requirePlayers() {
        conditions.add {
            if (sender !is Player) {
                sender.sendMessage(Component.text("This command must be executed by a player!", NamedTextColor.RED))
                false
            } else true
        }
    }

    fun requireConsole() {
        conditions.add {
            if (sender !is ConsoleSender) {
                sender.sendMessage(Component.text("This command must be executed in the console!", NamedTextColor.RED))
                false
            } else true
        }
    }

    fun requireInGame() {
        conditions.add {
            if (sender !is Player || Game.findGame(sender) == null) {
                sender.sendMessage(
                    Component.text(
                        "You are not in a game! Join a game in order to run this command.", NamedTextColor.RED
                    )
                )
                false
            } else true
        }
    }

}