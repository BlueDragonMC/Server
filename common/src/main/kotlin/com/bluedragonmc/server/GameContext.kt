package com.bluedragonmc.server

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.game.GameData
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.GameState
import net.minestom.server.adventure.audience.PacketGroupingAudience
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.instance.Instance
import java.time.Duration

/**
 * The subset of a [Game] that [GameModule]s are allowed to interact with.
 */
abstract class GameContext : ModuleHolder(), PacketGroupingAudience {

    abstract val id: String

    abstract val data: GameData

    abstract val players: List<Player>

    abstract var state: GameState

    abstract val rpcGameState: CommonTypes.GameState

    abstract fun callEvent(event: Event)

    abstract fun callCancellable(event: Event, successCallback: Runnable)

    abstract fun ownsInstance(instance: Instance): Boolean

    abstract fun getOwnedInstances(): List<Instance>

    abstract fun getRequiredInstances(): List<Instance>

    abstract fun getInstance(): Instance

    abstract fun unregister(module: GameModule)

    abstract fun endGame(queueAllPlayers: Boolean = true)

    abstract fun endGameLater(delay: Duration = Duration.ZERO)

    abstract fun isInactive(): Boolean
}
