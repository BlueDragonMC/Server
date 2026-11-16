package com.bluedragonmc.server.api

import com.bluedragonmc.api.grpc.GsClient
import com.bluedragonmc.api.grpc.PlayerHolderOuterClass.SendPlayerRequest
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.game.GameRequest
import net.minestom.server.entity.Player

abstract class Queue {

    abstract fun start()
    abstract fun queue(player: Player, request: GameRequest)
    abstract fun bulkEnqueue(requests: List<Pair<Player, GameRequest>>)

    fun queue(player: Player, game: String, mode: String? = null, mapId: String? = null) =
        queue(player, GameRequest.create(game, mode, mapId))

    open fun createInstance(request: GsClient.CreateInstanceRequest): Game? {
        throw NotImplementedError("Creating instances not implemented")
    }

    open fun sendPlayer(request: SendPlayerRequest) {}

}
