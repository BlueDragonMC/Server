package com.bluedragonmc.server.api

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.api.grpc.PartySvc
import com.bluedragonmc.api.grpc.PlayerTrackerOuterClass
import com.bluedragonmc.server.Game
import net.kyori.adventure.text.Component
import net.minestom.server.command.CommandSender
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.util.*

/**
 * Stub - no functionality. Used in development and testing environments.
 * See [com.bluedragonmc.server.impl.OutgoingRPCHandlerImpl]
 * for a full implementation.
 */
class OutgoingRPCHandlerStub : OutgoingRPCHandler {
    override fun isConnected(): Boolean {
        return true
    }

    override suspend fun initGameServer(serverName: String) {

    }

    override fun onGameCreated(game: Game) {

    }

    override suspend fun initGame(id: String, gameType: CommonTypes.GameType) {

    }

    override suspend fun updateGameState(id: String, gameState: CommonTypes.GameState) {

    }

    override suspend fun notifyInstanceRemoved(gameId: String) {

    }

    override suspend fun checkRemoveInstance(gameId: String): Boolean {
        return true
    }

    override suspend fun recordInstanceChange(player: Player, newGame: String) {

    }

    override suspend fun playerTransfer(player: Player, newGame: String?) {

    }

    override suspend fun queryPlayer(username: String?, uuid: UUID?): PlayerTrackerOuterClass.QueryPlayerResponse {
        return PlayerTrackerOuterClass.QueryPlayerResponse.getDefaultInstance()
    }

    override suspend fun addToQueue(player: Player, gameType: CommonTypes.GameType) {

    }

    override suspend fun removeFromQueue(player: Player) {

    }

    override suspend fun getDestination(player: UUID): String? {
        error("getDestination not implemented without gRPC messaging enabled!")
    }

    override suspend fun sendPrivateMessage(message: Component, sender: CommandSender, recipient: UUID) {

    }

    override suspend fun inviteToParty(partyOwner: UUID, invitee: UUID) {

    }

    override suspend fun acceptPartyInvitation(partyOwner: UUID, invitee: UUID) {

    }

    override suspend fun kickFromParty(partyOwner: UUID, player: UUID) {

    }

    override suspend fun partyChat(message: String, sender: Player) {

    }

    override suspend fun warpParty(partyOwner: Player, instance: Instance) {

    }

    override suspend fun transferParty(partyOwner: Player, newOwner: UUID) {

    }

    override suspend fun listPartyMembers(member: UUID): PartySvc.PartyListResponse {
        return PartySvc.PartyListResponse.getDefaultInstance()
    }

}
