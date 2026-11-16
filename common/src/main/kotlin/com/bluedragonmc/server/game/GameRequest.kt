package com.bluedragonmc.server.game

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.gameType

/**
 * A request to queue a player for a game. This class is a validated version of [CommonTypes.GameType].
 */
data class GameRequest(
    val game: String,
    val mode: GameModeId,
    val mapId: String? = null,
) {
    fun toProto(): CommonTypes.GameType = gameType {
        name = this@GameRequest.game
        mode = this@GameRequest.mode.value
        this@GameRequest.mapId?.let { this.mapId = it }
    }

    companion object {
        /**
         * Resolves a user/config-supplied game and mode into a canonical request.
         * A missing mode resolves to the game's declared default.
         *
         * @throws IllegalArgumentException if the game or mode is unknown.
         */
        fun create(game: String, mode: String? = null, mapId: String? = null): GameRequest {
            val gameName = game
            require(gameName.isNotEmpty()) { "Game name cannot be blank" }

            val requestedMode = mode?.trim()?.takeIf { it.isNotEmpty() }
            val resolved = if (requestedMode == null) {
                GameModes.defaultFor(gameName)
            } else {
                GameModes.resolve(gameName, requestedMode)
                    ?: throw IllegalArgumentException(
                        "Unknown mode '$requestedMode' for game '$gameName'. " +
                                "Valid modes: ${GameModes.validModeIds(gameName).joinToString(", ")}"
                    )
            }

            return GameRequest(gameName, resolved.id, mapId)
        }
    }
}
