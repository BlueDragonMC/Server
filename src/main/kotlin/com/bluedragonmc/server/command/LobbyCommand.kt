package com.bluedragonmc.server.command

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.service.Messaging

class LobbyCommand(name: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, block = {
    requirePlayers()
    suspendSyntax {
        val localLobby = Game.games.filter { it.data.name == Environment.defaultGameName }.randomOrNull()

        if (localLobby == null) {
            Messaging.outgoing.addToQueue(player, CommonTypes.GameType.newBuilder().setName("Lobby").build())
            return@suspendSyntax
        }

        if (player.instance == localLobby.getInstance()) {
            val pos = localLobby.getModuleOrNull<SpawnpointModule>()?.spawnpointProvider?.getSpawnpoint(player)
            if (pos != null) {
                player.teleport(pos)
            } else {
                player.sendMessage(formatErrorTranslated("command.lobby.already_in_lobby"))
            }
            return@suspendSyntax
        }
        localLobby.addPlayer(player)
        // Remove the player from the queue when they go to the lobby
        Messaging.outgoing.removeFromQueue(player)
    }
})