package com.bluedragonmc.server.api

import com.bluedragonmc.api.grpc.CommonTypes.GameState
import com.bluedragonmc.api.grpc.CommonTypes.GameType
import com.bluedragonmc.api.grpc.PartySvc.PartyListResponse
import com.bluedragonmc.api.grpc.PlayerTrackerOuterClass.QueryPlayerResponse
import com.bluedragonmc.server.Game
import net.kyori.adventure.text.Component
import net.minestom.server.command.CommandSender
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.util.*

/**
 * Represents a messaging handler that sends messages
 * from this service to other services.
 */
interface OutgoingRPCHandler {

    fun isConnected(): Boolean

    suspend fun initGameServer(serverName: String)

    // Instance tracking
    fun onGameCreated(game: Game)
    suspend fun initInstance(instance: Instance, gameType: GameType)
    suspend fun updateGameState(instance: Instance, gameState: GameState)
    suspend fun notifyInstanceRemoved(instanceId: UUID)

    // Player tracking
    suspend fun recordInstanceChange(player: Player, newInstance: Instance)
    suspend fun playerTransfer(player: Player, newInstance: Instance?)
    suspend fun queryPlayer(username: String? = null, uuid: UUID? = null): QueryPlayerResponse

    // Queue
    suspend fun addToQueue(player: Player, gameType: GameType)
    suspend fun removeFromQueue(player: Player)

    // Private messaging
    suspend fun sendPrivateMessage(message: Component, sender: CommandSender, recipient: UUID)

    // Party system
    suspend fun inviteToParty(partyOwner: UUID, invitee: UUID)
    suspend fun acceptPartyInvitation(partyOwner: UUID, invitee: UUID)
    suspend fun kickFromParty(partyOwner: UUID, player: UUID)
    suspend fun partyChat(message: String, sender: Player)
    suspend fun warpParty(partyOwner: Player, instance: Instance)
    suspend fun transferParty(partyOwner: Player, newOwner: UUID)
    suspend fun listPartyMembers(member: UUID): PartyListResponse

}