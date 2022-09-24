package com.bluedragonmc.server.command

import com.bluedragonmc.messages.RequestRemoveFromQueueMessage
import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.module.minigame.SpawnpointModule

class LobbyCommand(name: String, vararg aliases: String?) : BlueDragonCommand(name, aliases, null, block = {
    requirePlayers()
    syntax {
        if (player.instance == lobby.getInstance()) {
            val pos = lobby.getModuleOrNull<SpawnpointModule>()?.spawnpointProvider?.getSpawnpoint(player)
            if (pos != null) {
                player.teleport(pos)
            } else {
                player.sendMessage(formatErrorTranslated("command.lobby.already_in_lobby"))
            }
            return@syntax
        }
        player.setInstance(
            lobby.getInstance(), lobby.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(player)
        )
        // Remove the player from the queue when they go to the lobby
        MessagingModule.publish(RequestRemoveFromQueueMessage(player.uuid))
    }
})