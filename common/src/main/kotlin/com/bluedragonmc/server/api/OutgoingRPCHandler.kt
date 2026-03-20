package com.bluedragonmc.server.api

import com.bluedragonmc.api.grpc.CommonTypes.GameState
import com.bluedragonmc.api.grpc.CommonTypes.GameType
import com.bluedragonmc.api.grpc.JukeboxOuterClass.PlayerSongQueue
import com.bluedragonmc.api.grpc.PartySvc.PartyListResponse
import com.bluedragonmc.api.grpc.PlayerTrackerOuterClass.QueryPlayerResponse
import com.bluedragonmc.server.Game
import net.kyori.adventure.text.Component
import net.minestom.server.command.CommandSender
import net.minestom.server.entity.Player
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
    suspend fun initGame(id: String, gameType: GameType, gameState: GameState)
    suspend fun updateGameState(id: String, gameState: GameState)
    suspend fun notifyInstanceRemoved(gameId: String)
    suspend fun checkRemoveInstance(gameId: String): Boolean

    // Player tracking
    suspend fun recordInstanceChange(player: Player, newGame: String)
    suspend fun playerTransfer(player: Player, newGame: String?)
    suspend fun queryPlayer(username: String? = null, uuid: UUID? = null): QueryPlayerResponse

    // Queue
    suspend fun addToQueue(player: Player, gameType: GameType)
    suspend fun removeFromQueue(player: Player)
    suspend fun getDestination(player: UUID): String?

    // Private messaging
    suspend fun sendPrivateMessage(message: Component, sender: CommandSender, recipient: UUID)

    // Party system
    suspend fun inviteToParty(partyOwner: UUID, invitee: UUID)
    suspend fun acceptPartyInvitation(partyOwner: UUID, invitee: UUID)
    suspend fun kickFromParty(partyOwner: UUID, player: UUID)
    suspend fun leaveParty(player: UUID)
    suspend fun partyChat(message: String, sender: Player)
    suspend fun warpParty(partyOwner: Player, gameId: String)
    suspend fun transferParty(partyOwner: Player, newOwner: UUID)
    suspend fun listPartyMembers(member: UUID): PartyListResponse

    // Party Marathons
    suspend fun startMarathon(player: UUID, durationMs: Int)
    suspend fun endMarathon(player: UUID)
    suspend fun getMarathonLeaderboard(players: Collection<UUID>, silent: Boolean)
    suspend fun recordCoinAward(player: UUID, coins: Int, gameId: String)

    // Jukebox controls
    suspend fun getSongInfo(player: Player): PlayerSongQueue
    suspend fun setSongInfo(player: Player, songQueue: PlayerSongQueue)
}