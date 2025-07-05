package com.bluedragonmc.server.command

import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.minigame.SpawnpointModule

class LobbyCommand(name: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, block = {
    requirePlayers()
    suspendSyntax {
        if (player.instance == lobby.getInstance()) {
            val pos = lobby.getModuleOrNull<SpawnpointModule>()?.spawnpointProvider?.getSpawnpoint(player)
            if (pos != null) {
                player.teleport(pos)
            } else {
                player.sendMessage(formatErrorTranslated("command.lobby.already_in_lobby"))
            }
            return@suspendSyntax
        }
        lobby.addPlayer(player)
        // Remove the player from the queue when they go to the lobby
        Messaging.outgoing.removeFromQueue(player)
    }
})