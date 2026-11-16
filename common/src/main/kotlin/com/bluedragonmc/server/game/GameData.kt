package com.bluedragonmc.server.game

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.server.service.Maps

data class GameData(
    val name: String,
    val mapSource: Maps.MapSource,
    val mode: GameModeId,
) {
    val gameType: CommonTypes.GameType
        get() = gameType {
            name = this@GameData.name
            mode = this@GameData.mode.value
            mapId = mapSource.id
        }
}
