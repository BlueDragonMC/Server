package com.bluedragonmc.server.api

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.GsClient
import com.bluedragonmc.api.grpc.PlayerHolderOuterClass.SendPlayerRequest
import com.bluedragonmc.server.Game
import net.minestom.server.entity.Player
import java.io.File

abstract class Queue {

    abstract fun start()
    abstract fun queue(player: Player, gameType: CommonTypes.GameType)

    abstract fun getMaps(gameType: String): Array<File>?
    abstract fun randomMap(gameType: String): String?

    open fun createInstance(request: GsClient.CreateInstanceRequest): Game? {
        throw NotImplementedError("Creating instances not implemented")
    }

    open fun sendPlayer(request: SendPlayerRequest) {}

}