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
    get() = requireModule(PlayerListModule::class).players

/** The audience that broadcasts to every player in this holder. */
val ModuleHolder.audience: PacketGroupingAudience
    get() = requireModule(PlayerListModule::class)

/** The current game state. */
var ModuleHolder.state: GameState
    get() = requireModule(GameStateModule::class).state
    set(value) {
        requireModule(GameStateModule::class).state = value
    }

/** The unique identifier of the game. */
val ModuleHolder.id: String
    get() = requireModule(GameInfoModule::class).id

/** The metadata of the game. */
val ModuleHolder.data: GameData
    get() = requireModule(GameInfoModule::class).data

/** The RPC representation of the game state. */
val ModuleHolder.rpcGameState: CommonTypes.GameState
    get() = requireModule(GameInfoModule::class).rpcGameState

/** The single instance owned by this holder. */
fun ModuleHolder.getInstance(): Instance = requireModule(InstanceModule::class).getInstance()

/** Every instance owned by this holder. */
fun ModuleHolder.getOwnedInstances(): List<Instance> = requireModule(InstanceModule::class).getOwnedInstances()

/** Ends the holder's game. */
fun ModuleHolder.endGame(queueAllPlayers: Boolean = true) = requireModule(GameStateModule::class).endGame(queueAllPlayers)

/** Ends the holder's game after [delay]. */
fun ModuleHolder.endGameLater(delay: Duration = Duration.ZERO) = requireModule(GameStateModule::class).endGameLater(delay)
