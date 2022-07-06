package com.bluedragonmc.server.command

import com.bluedragonmc.server.lobby
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.entity.Player

class LobbyCommand(name: String, usage: String, vararg aliases: String?) : BlueDragonCommand(name, usage, *aliases) {
    init {
        setDefaultExecutor { sender: CommandSender, context: CommandContext ->
            if (sender !is Player) return@setDefaultExecutor
            sender.setInstance(lobby.getInstance())
        }
    }

}