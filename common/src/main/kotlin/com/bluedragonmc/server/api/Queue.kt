package com.bluedragonmc.server.api

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.GsClient
import com.bluedragonmc.api.grpc.PlayerHolderOuterClass.SendPlayerRequest
import com.bluedragonmc.server.Game
import net.minestom.server.entity.Player

abstract class Queue {

    abstract fun start()
    abstract fun queue(player: Player, gameType: CommonTypes.GameType)
    abstract fun bulkEnqueue(requests: List<Pair<Player, CommonTypes.GameType>>)

    open fun createInstance(request: GsClient.CreateInstanceRequest): Game? {
        throw NotImplementedError("Creating instances not implemented")
    }

    open fun sendPlayer(request: SendPlayerRequest) {}

}