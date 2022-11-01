package com.bluedragonmc.server.command

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.command.BlueDragonCommand.Companion.errorColor
import com.bluedragonmc.server.module.database.Permissions
import com.bluedragonmc.server.module.database.PlayerDocument
import com.bluedragonmc.server.utils.component1
import com.bluedragonmc.server.utils.component2
import com.bluedragonmc.server.utils.component3
import com.bluedragonmc.server.utils.withColor
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandSender
import net.minestom.server.command.ConsoleSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.arguments.Argument
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.utils.entity.EntityFinder

/**
 * A basic command class that is extended by BlueDragon commands.
 */
open class BlueDragonCommand(
    name: String,
    aliases: Array<out String?> = emptyArray(),
    val permission: String? = "command.$name",
    block: BlueDragonCommand.() -> Unit,
) : Command(name, *aliases), ConditionHolder {

    companion object {
        val messageColor = BRAND_COLOR_PRIMARY_2
        val fieldColor = BRAND_COLOR_PRIMARY_1
        val errorColor: TextColor = NamedTextColor.RED
        val errorFieldColor: TextColor = NamedTextColor.DARK_RED
        internal fun buildMessage(block: MessageBuilder.() -> Unit) = MessageBuilder().apply(block).get()
    }

    internal class MessageBuilder {
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

    fun formatPos(pos: Point): String {
        val (x, y, z) = pos
        return String.format("(%.1f, %.1f, %.1f)", x, y, z)
    }

    @Deprecated("Translatable messages are strongly prefered.")
    fun formatMessage(string: String, vararg fields: Any): Component =
        formatMessage(string, messageColor, fieldColor, *fields)

    fun formatMessageTranslated(key: String, vararg fields: Any): Component =
        formatTranslatedMessage(key, messageColor, fieldColor, *fields)

    fun formatErrorTranslated(key: String, vararg fields: Any): Component =
        formatTranslatedMessage(key, errorColor, errorFieldColor, *fields)

    @Deprecated("Translatable messages are strongly prefered.")
    fun formatErrorMessage(string: String, vararg fields: Any): Component =
        formatMessage(string, errorColor, errorFieldColor, *fields)

    private fun formatTranslatedMessage(
        key: String,
        messageColor: TextColor,
        fieldColor: TextColor,
        vararg values: Any,
    ): Component = Component.translatable(key, messageColor, values.map {
        if (it is Component) it else Component.text(it.toString(), fieldColor)
    })

    private fun formatMessage(
        string: String,
        messageColor: TextColor,
        fieldColor: TextColor,
        vararg fields: Any,
    ): Component {
        val split = string.split("{}")

        if (split.size == 1) return string withColor messageColor
        return Companion.buildMessage {
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
        if (sender is ConsoleSender) {
            block(CommandCtx(sender, context))
            return@setDefaultExecutor
        }
        sender as CustomPlayer
        if (permission == null || Permissions.hasPermission(sender.data, permission))
            block(CommandCtx(sender, context))
        else sender.sendMessage(Component.translatable("commands.help.failed", errorColor))
    }

    fun usage(usageString: String) = usage {
        sender.sendMessage("Usage: $usageString" withColor errorColor)
    }

    fun subcommand(name: String, block: BlueDragonCommand.() -> Unit) =
        addSubcommand(constructSubcommand(name, block))

    fun constructSubcommand(name: String, block: BlueDragonCommand.() -> Unit) =
        BlueDragonCommand(name, emptyArray(), permission, block)

    fun syntax(vararg args: Argument<*>, block: CommandCtx.() -> Unit) = Syntax(this, args.toList(), block)
    fun suspendSyntax(vararg args: Argument<*>, block: suspend CommandCtx.() -> Unit) =
        BlockingSyntax(this, args.toList(), block)

    fun userSuspendSyntax(vararg args: Argument<*>, block: suspend UserCommandCtx.() -> Unit) =
        suspendSyntax(*args, block = {
            block(UserCommandCtx(sender, ctx, args.first() as ArgumentOfflinePlayer))
        })

    class UserCommandCtx(sender: CommandSender, ctx: CommandContext, userArgument: Argument<PlayerDocument>) :
        CommandCtx(sender, ctx) {
        val doc = get(userArgument)
    }

    open class CommandCtx(val sender: CommandSender, val ctx: CommandContext) {
        val player by lazy { sender as Player }
        val game by lazy { Game.findGame(player)!! }
        val playerName by lazy { (sender as? Player)?.name ?: Component.translatable("command.console_sender_name") }

        fun <T> get(argument: Argument<T>): T = ctx.get(argument)
        fun getPlayer(argument: Argument<PlayerDocument>): Player? =
            MinecraftServer.getConnectionManager().getPlayer(get(argument).uuid)

        fun getFirstPlayer(argument: Argument<EntityFinder>): Player =
            ctx.get(argument).findFirstPlayer(sender) ?: run {
                sender.sendMessage("That player was not found!" withColor errorColor)
                throw SilentCommandException("No player found")
            }
    }

    data class ConditionCtx(val sender: CommandSender, val ctx: CommandContext)

    open class Syntax(val parent: Command, val args: List<Argument<*>>, val handler: CommandCtx.() -> Unit) :
        ConditionHolder {
        override val conditions: MutableList<ConditionCtx.() -> Boolean> = mutableListOf()

        init {
            val permission = (parent as BlueDragonCommand).permission
            if (permission != null) this.requirePermission(permission)
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

    class BlockingSyntax(parent: Command, args: List<Argument<*>>, handler: suspend CommandCtx.() -> Unit) :
        Syntax(parent, args, {
            runBlocking {
                handler()
            }
        })

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
                sender.sendMessage("You are not in a game! Join a game in order to run this command." withColor errorColor)
                false
            } else true
        }
    }

    fun requirePermission(
        permission: String,
        noPermissionMessage: Component = Component.translatable("commands.help.failed", errorColor),
    ) {
        conditions.add {
            if (sender is CustomPlayer && !Permissions.hasPermission(sender.data, permission)) {
                sender.sendMessage(noPermissionMessage withColor errorColor)
                false
            } else true // Console always has permission
        }
    }

}