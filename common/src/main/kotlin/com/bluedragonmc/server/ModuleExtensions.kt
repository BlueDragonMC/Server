package com.bluedragonmc.server

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.game.GameData
import com.bluedragonmc.server.module.GameInfoModule
import com.bluedragonmc.server.module.GameStateModule
import com.bluedragonmc.server.module.PlayerListModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.utils.GameState
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.time.Duration

/**
 * Convenience accessors for various game capabilities exposed
 * by the [ModuleHolder]'s provider modules.
 */

/** The players that belong to this holder. */
val ModuleHolder.players: List<Player>
    get() = getModule<PlayerListModule>().players

/** The audience that broadcasts to every player in this holder. */
val ModuleHolder.audience: PacketGroupingAudience
    get() = getModule<PlayerListModule>()

/** The current game state. */
var ModuleHolder.state: GameState
    get() = getModule<GameStateModule>().state
    set(value) {
        getModule<GameStateModule>().state = value
    }

/** The unique identifier of the game. */
val ModuleHolder.id: String
    get() = getModule<GameInfoModule>().id

/** The metadata of the game. */
val ModuleHolder.data: GameData
    get() = getModule<GameInfoModule>().data

/** The RPC representation of the game state. */
val ModuleHolder.rpcGameState: CommonTypes.GameState
    get() = getModule<GameInfoModule>().rpcGameState

/** The single instance owned by this holder. */
fun ModuleHolder.getInstance(): Instance = getModule<InstanceModule>().getInstance()

/** Every instance owned by this holder. */
fun ModuleHolder.getOwnedInstances(): List<Instance> = getModule<InstanceModule>().getOwnedInstances()

/** Ends the holder's game. */
fun ModuleHolder.endGame(queueAllPlayers: Boolean = true) = getModule<GameStateModule>().endGame(queueAllPlayers)

/** Ends the holder's game after [delay]. */
fun ModuleHolder.endGameLater(delay: Duration = Duration.ZERO) = getModule<GameStateModule>().endGameLater(delay)
