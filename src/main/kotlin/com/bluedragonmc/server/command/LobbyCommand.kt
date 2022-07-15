package com.bluedragonmc.server.command

import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.gameplay.SpawnpointModule

class LobbyCommand(name: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, {
    requirePlayers()
    syntax {
        if (player.instance == lobby.getInstance()) {
            player.sendMessage(formatErrorMessage("You are already in the lobby!"))
            return@syntax
        }
        player.setInstance(
            lobby.getInstance(), lobby.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(player)
        )
    }
})