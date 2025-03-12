package com.bluedragonmc.server.command

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule.Companion.MAP_NAME_TAG
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.utils.*
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
                    // Instance ID
                    +text(instance.uuid.toString(), NamedTextColor.DARK_GRAY)
                        .clickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, instance.uuid.toString())
                    +text(" · ", NamedTextColor.GRAY)
                    // Instance class name
                    +text(instance::class.simpleName.toString(), NamedTextColor.AQUA)
                    +newline()
                    +text(" → ", NamedTextColor.GRAY)
                    val game = Game.findGame(instance.uuid)
                    if (instance.hasTag(MAP_NAME_TAG)) {
                        +translatable(
                            "command.instance.instance_container", NamedTextColor.GRAY,
                            text(instance.getTag(MAP_NAME_TAG), BRAND_COLOR_PRIMARY_1)
                        )
                    } else {
                        // Game name
                        if (game?.name != null) {
                            +text(game.name, NamedTextColor.YELLOW)
                            if (game.mode != null) {
                                +text(" (", NamedTextColor.GRAY)
                                +text(game.mode!!, NamedTextColor.DARK_GREEN)
                                +text(")", NamedTextColor.GRAY)
                            }
                        } else {
                            +translatable("command.instance.no_game", NamedTextColor.RED)
                        }
                        +text(" · ", NamedTextColor.GRAY)
                        // Map name
                        if (game?.mapName != null) {
                            +text(game.mapName, NamedTextColor.GOLD)
                        } else {
                            +translatable("command.instance.no_map", NamedTextColor.RED)
                        }
                        +text(" · ", NamedTextColor.GRAY)
                        // Online players
                        +translatable("command.instance.players", NamedTextColor.GRAY, text(instance.players.size))
                        +space()
                        val connectButtonColor =
                            if (sender is Player && player.instance != instance) NamedTextColor.YELLOW else NamedTextColor.GRAY
                        +translatable("command.instance.action.connect", connectButtonColor)
                            .hoverEventTranslatable("command.instance.action.connect.hover", NamedTextColor.YELLOW)
                            .clickEvent("/instance join ${instance.uuid}")
                    }
                    val requiredBy = Game.games.filter { it.getRequiredInstances().contains(instance) }
                    if (requiredBy.isNotEmpty()) requiredBy.forEach { game ->
                        +newline()
                        +text(" → ", NamedTextColor.GRAY)
                        +translatable(
                            "command.instance.required_by",
                            NamedTextColor.GRAY,
                            text(game.id, BRAND_COLOR_PRIMARY_1).hoverEvent(
                                text(game.name, NamedTextColor.YELLOW) +
                                        text(" · ", NamedTextColor.GRAY) +
                                        text(game.mapName, NamedTextColor.GOLD) +
                                        text(" · ", NamedTextColor.GRAY) +
                                        text(game.mode ?: "--", NamedTextColor.DARK_GREEN)
                            )
                        )
                    }
                }
            }.surroundWithSeparators()

            sender.sendMessage(component)
        }
    }

    val instanceArgument by InstanceArgument

    subcommand("join") {
        syntax(instanceArgument) {
            val instance = get(instanceArgument)
            player.sendMessage(formatMessageTranslated("queue.sending", instance.uuid))
            try {
                val spawnpoint = Game.findGame(instance.uuid)
                    ?.getModuleOrNull<SpawnpointModule>()
                    ?.spawnpointProvider
                    ?.getSpawnpoint(player)

                val completableFuture = if (spawnpoint == null) {
                    player.setInstance(instance)
                } else {
                    player.setInstance(instance, spawnpoint)
                }

                completableFuture.whenCompleteAsync { _, throwable ->
                    // Send a generic error message
                    throwable?.let {
                        player.sendMessage(
                            formatErrorTranslated(
                                "command.instance.join.fail.generic",
                                instance.uuid
                            )
                        )
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
                player.sendMessage(formatMessageTranslated("command.instance.remove.success", instance.uuid))
            }
        }
    }
})
