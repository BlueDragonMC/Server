package com.bluedragonmc.server.command

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.command.BlueDragonCommand.Companion.errorColor
import com.bluedragonmc.server.utils.withColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
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

    companion object {
        val messageColor = BRAND_COLOR_PRIMARY_2
        val fieldColor = BRAND_COLOR_PRIMARY_1
        val errorColor: TextColor = NamedTextColor.RED
        val errorFieldColor: TextColor = NamedTextColor.DARK_RED
    }

    private class MessageBuilder {
        private val components = mutableListOf<Component>()

        fun message(string: String) {
            components.add(string withColor messageColor)
        }

        fun component(component: Component, optionalColor: TextColor = fieldColor) {
            components.add(component.colorIfAbsent(optionalColor))
        }

        fun field(string: String) {
            components.add(string withColor fieldColor)
        }

        fun error(string: String) {
            components.add(string withColor errorColor)
        }

        fun errorField(string: String) {
            components.add(string withColor errorFieldColor)
        }

        fun get() = Component.join(JoinConfiguration.noSeparators(), components)
    }

    private fun buildMessage(block: MessageBuilder.() -> Unit) = MessageBuilder().apply(block).get()

    fun formatMessage(string: String, vararg fields: Any): Component = formatMessage(string, messageColor, fieldColor, *fields)
    fun formatErrorMessage(string: String, vararg fields: Any): Component = formatMessage(string, errorColor, errorFieldColor, *fields)

    private fun formatMessage(string: String, messageColor: TextColor, fieldColor: TextColor, vararg fields: Any): Component {
        val split = string.split("{}")

        if (split.size == 1) return string withColor messageColor
        return buildMessage {
            for ((index, part) in split.withIndex()) {
                message(part)
                if (index < fields.size) {
                    when (val field = fields[index]) {
                        is Component -> component(field, fieldColor)
                        else -> field(field.toString())
                    }
                }
            }
        }
    }

    override val conditions: MutableList<ConditionCtx.() -> Boolean> = mutableListOf()

    init {
        block()
    }

    fun usage(block: CommandCtx.() -> Unit) = setDefaultExecutor { sender, context ->
        block(CommandCtx(sender, context))
    }

    fun usage(usageString: String) = usage {
        sender.sendMessage("Usage: $usageString" withColor errorColor)
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
                sender.sendMessage("That player was not found!" withColor errorColor)
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
                    if (e is SilentCommandException) return@addSyntax
                    e.printStackTrace()
                    sender.sendMessage("There was an internal error executing this command." withColor errorColor)
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
                sender.sendMessage("This command must be executed by a player!" withColor errorColor)
                false
            } else true
        }
    }

    fun requireConsole() {
        conditions.add {
            if (sender !is ConsoleSender) {
                sender.sendMessage("This command must be executed in the console!" withColor errorColor)
                false
            } else true
        }
    }

    fun requireInGame() {
        conditions.add {
            if (sender !is Player || Game.findGame(sender) == null) {
                sender.sendMessage(
                    "You are not in a game! Join a game in order to run this command." withColor errorColor
                )
                false
            } else true
        }
    }

}