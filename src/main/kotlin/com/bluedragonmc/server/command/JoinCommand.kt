package com.bluedragonmc.server.command

import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.game.GameMode
import com.bluedragonmc.server.game.GameModes
import com.bluedragonmc.server.game.GameRequest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.arguments.ArgumentWord

class JoinCommand(name: String, private val usageString: String, vararg aliases: String) : BlueDragonCommand(name, aliases, block = {

    val gameArgument = ArgumentWord("game").from(*Environment.gameClasses.toTypedArray())
    val modeArgument by GameModeArgument
    val mapArgument by WordArgument

    usage(usageString)

    syntax(gameArgument) {
        Environment.queue.queue(player, get(gameArgument))
    }.requirePlayers()

    syntax(gameArgument, modeArgument) {
        val game = get(gameArgument)
        val mode = resolveMode(player, game, get(modeArgument)) ?: return@syntax
        Environment.queue.queue(player, GameRequest(game, mode.id))
    }.apply {
        requirePlayers()
        requirePermission("command.join.mode", Component.translatable("command.join.no_mode_permission", NamedTextColor.RED))
    }

    syntax(gameArgument, modeArgument, mapArgument) {
        val game = get(gameArgument)
        val mode = resolveMode(player, game, get(modeArgument)) ?: return@syntax
        Environment.queue.queue(player, GameRequest(game, mode.id, get(mapArgument)))
    }.apply {
        requirePlayers()
        requirePermission("command.join.mode", Component.translatable("command.join.no_mode_permission", NamedTextColor.RED))
        requirePermission("command.join.map", Component.translatable("command.join.no_map_permission", NamedTextColor.RED))
    }
})

private fun resolveMode(sender: CommandSender, game: String, rawMode: String): GameMode? {
    val mode = GameModes.resolve(game, rawMode)
    if (mode == null) {
        val validModes = GameModes.validModeIds(game)
        sender.sendMessage(
            if (validModes.isEmpty()) {
                Component.translatable(
                    "command.join.no_modes",
                    NamedTextColor.RED,
                    Component.text(game),
                )
            } else {
                Component.translatable(
                    "command.join.invalid_mode",
                    NamedTextColor.RED,
                    Component.text(rawMode),
                    Component.text(game),
                    Component.text(validModes.joinToString(", ")),
                )
            }
        )
    }
    return mode
}
