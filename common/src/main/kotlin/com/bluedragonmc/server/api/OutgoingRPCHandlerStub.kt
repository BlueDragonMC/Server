package com.bluedragonmc.server.api

import com.bluedragonmc.api.grpc.*
import com.bluedragonmc.api.grpc.Map
import com.bluedragonmc.server.Game
import net.kyori.adventure.text.Component
import net.minestom.server.command.CommandSender
import net.minestom.server.entity.Player
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.name

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

    override suspend fun initGame(id: String, gameType: CommonTypes.GameType, gameState: CommonTypes.GameState) {

    }

    override suspend fun updateGameState(id: String, gameState: CommonTypes.GameState) {

    }

    override suspend fun notifyInstanceRemoved(gameId: String) {

    }

    override suspend fun recordInstanceChange(player: Player, newGame: String) {

    }

    override suspend fun playerTransfer(player: Player, newGame: String?) {

    }

    override suspend fun queryPlayer(username: String?, uuid: UUID?): PlayerTrackerOuterClass.QueryPlayerResponse {
        return PlayerTrackerOuterClass.QueryPlayerResponse.getDefaultInstance()
    }

    override suspend fun getAvailableMaps(gameName: String?, gameMode: String?, whitelist: List<UUID>?): Map.MapList {
        val mapDefs = File("worlds").listFiles()
            .flatMap { it.listFiles().map { file -> file.absolutePath } }
            .map { mapFolderPath ->
                CommonTypes.MapSource.newBuilder()
                    .setMapId(Paths.get(mapFolderPath).name)
                    .setMapConfig(File(mapFolderPath, "config.yml").readText())
                    .setMapFormat(CommonTypes.MapFormat.ANVIL)
                    .setMapUrl("file://$mapFolderPath")
                    .build()
            }
        return com.bluedragonmc.api.grpc.Map.MapList.newBuilder().addAllMaps(mapDefs).build()
    }

    override suspend fun updateMapConfig(mapId: String, configJson: String) {

    }

    override suspend fun addToQueue(player: Player, gameType: CommonTypes.GameType) {

    }

    override suspend fun bulkAddToQueue(messages: List<Pair<Player, CommonTypes.GameType>>) {

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

    override suspend fun leaveParty(player: UUID) {

    }

    override suspend fun partyChat(message: String, sender: Player) {

    }

    override suspend fun warpParty(partyOwner: Player, gameId: String) {

    }

    override suspend fun transferParty(partyOwner: Player, newOwner: UUID) {

    }

    override suspend fun listPartyMembers(member: UUID): PartySvc.PartyListResponse {
        return PartySvc.PartyListResponse.getDefaultInstance()
    }

    override suspend fun startMarathon(player: UUID, durationMs: Int) {

    }

    override suspend fun endMarathon(player: UUID) {

    }

    override suspend fun getMarathonLeaderboard(players: Collection<UUID>, silent: Boolean) {

    }

    override suspend fun recordCoinAward(player: UUID, coins: Int, gameId: String) {

    }

    override suspend fun getSongInfo(player: Player): JukeboxOuterClass.PlayerSongQueue {
        return playerSongQueue {
            isPlaying = false
        }
    }

    override suspend fun setSongInfo(
        player: Player,
        songQueue: JukeboxOuterClass.PlayerSongQueue
    ) {

    }
}
