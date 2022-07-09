package com.bluedragonmc.server.command

import com.bluedragonmc.server.lobby
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class LobbyCommand(name: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, {
    requirePlayers()
    syntax {
        if(player.instance == lobby.getInstance()) {
            player.sendMessage(Component.text("You are already in the lobby!", NamedTextColor.RED))
            return@syntax
        }
        player.setInstance(lobby.getInstance())
    }
})