package com.bluedragonmc.server.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.Command

/**
 * A basic command class that is extended by BlueDragon commands.
 */
open class BlueDragonCommand(name: String, private val usage: String, vararg aliases: String?) :
    Command(name, *aliases) {
    init {
        setDefaultExecutor { sender, _ ->
            sender.sendMessage(Component.text("Usage: $usage").color(NamedTextColor.RED))
        }
    }
}