package com.bluedragonmc.server.command

import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.gameplay.SpawnpointModule

class LobbyCommand(name: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, null, block = {
    requirePlayers()
    syntax {
        if (player.instance == lobby.getInstance()) {
            player.sendMessage(formatErrorTranslated("command.lobby.already_in_lobby"))
            return@syntax
        }
        player.setInstance(
            lobby.getInstance(), lobby.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(player)
        )
    }
})