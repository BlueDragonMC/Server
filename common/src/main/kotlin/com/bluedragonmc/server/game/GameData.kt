package com.bluedragonmc.server.game

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.gameType
import com.bluedragonmc.server.service.Maps

data class GameData(
    val name: String,
    val mapSource: Maps.MapSource,
    val mode: String? = null,
) {
    val gameType: CommonTypes.GameType
        get() = gameType {
            name = this@GameData.name
            mapId = mapSource.id
            if (this@GameData.mode != null) {
                mode = this@GameData.mode
            }
        }
}