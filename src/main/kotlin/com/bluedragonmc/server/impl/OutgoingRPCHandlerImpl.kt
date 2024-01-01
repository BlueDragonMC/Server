package com.bluedragonmc.server.impl

import com.bluedragonmc.api.grpc.*
import com.bluedragonmc.api.grpc.Queue
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.OutgoingRPCHandler
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.model.EventLog
import com.bluedragonmc.server.model.Severity
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.listen
import com.bluedragonmc.server.utils.listenSuspend
import com.bluedragonmc.server.utils.miniMessage
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandSender
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.AddEntityToInstanceEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.Instance
import java.util.*
import java.util.concurrent.TimeUnit

class OutgoingRPCHandlerImpl(serverAddress: String) : OutgoingRPCHandler {

    private lateinit var serverName: String

    private val channel =
        ManagedChannelBuilder.forAddress(serverAddress, 50051)
            .defaultLoadBalancingPolicy("round_robin")
            .usePlaintext()
            .enableRetry()
            .build()

    init {
        MinecraftServer.getSchedulerManager().buildShutdownTask {
            channel.shutdown()
            channel.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    @DependsOn(InstanceModule::class)
    class MessagingModule : GameModule() {

        private lateinit var parent: Game

        override fun initialize(parent: Game, eventNode: EventNode<Event>): Unit = runBlocking {
            this@MessagingModule.parent = parent
            Messaging.outgoing.initGame(parent.id, parent.gameType)

            eventNode.listenSuspend<GameStateChangedEvent> { event ->
                Messaging.outgoing.updateGameState(parent.id, event.rpcGameState)
            }

            eventNode.listenSuspend<AddEntityToInstanceEvent> { event ->
                val gameId = Game.findGame(event.instance.uniqueId)?.id
                if (gameId != null) {
                    Messaging.outgoing.recordInstanceChange(event.entity as? Player ?: return@listenSuspend, gameId)
                }
            }

            eventNode.listen<PlayerSpawnEvent> { event ->
                MinecraftServer.getSchedulerManager().scheduleNextTick {
                    Messaging.IO.launch {
                        Messaging.outgoing.updateGameState(parent.id, parent.rpcGameState)
                    }
                }
            }

            eventNode.listen<PlayerDisconnectEvent> { event ->
                MinecraftServer.getSchedulerManager().scheduleNextTick {
                    Messaging.IO.launch {
                        Messaging.outgoing.updateGameState(parent.id, parent.rpcGameState)
                    }
                }
                Database.IO.launch {
                    Database.connection.logEvent(
                        EventLog("player_logout", Severity.DEBUG)
                            .withProperty("player_uuid", event.player.uuid.toString())
                    )
                }
            }
        }

        override fun deinitialize(): Unit = runBlocking {
            Messaging.outgoing.notifyInstanceRemoved(parent.id)

            Database.IO.launch {
                Database.connection.logEvent(
                    EventLog("game_removed", Severity.DEBUG)
                        .withProperty("game_id", parent.id)
                )
            }
        }
    }

    private val instanceSvcStub = InstanceServiceGrpcKt.InstanceServiceCoroutineStub(channel)
    private val gameStateSvcStub = GameStateServiceGrpcKt.GameStateServiceCoroutineStub(channel)
    private val privateMessageStub = VelocityMessageServiceGrpcKt.VelocityMessageServiceCoroutineStub(channel)
    private val playerTrackerStub = PlayerTrackerGrpcKt.PlayerTrackerCoroutineStub(channel)
    private val queueStub = QueueServiceGrpcKt.QueueServiceCoroutineStub(channel)
    private val partyStub = PartyServiceGrpcKt.PartyServiceCoroutineStub(channel)
    private val jukeboxStub = JukeboxGrpcKt.JukeboxCoroutineStub(channel)

    override fun isConnected(): Boolean {
        return !channel.isShutdown && !channel.isTerminated && ::serverName.isInitialized
    }

    override suspend fun initGameServer(serverName: String) {
        instanceSvcStub.initGameServer(
            ServerTracking.InitGameServerRequest.newBuilder()
                .setServerName(serverName)
                .build()
        )
        this.serverName = serverName
    }

    override fun onGameCreated(game: Game) {
        game.use(MessagingModule())
    }

    override suspend fun initGame(id: String, gameType: CommonTypes.GameType) {
        instanceSvcStub.createInstance(
            ServerTracking.InstanceCreatedRequest.newBuilder()
                .setInstanceUuid(id)
                .setGameType(gameType)
                .setServerName(serverName)
                .build()
        )
    }

    override suspend fun updateGameState(id: String, gameState: CommonTypes.GameState) {
        gameStateSvcStub.updateGameState(
            ServerTracking.GameStateUpdateRequest.newBuilder()
                .setServerName(serverName)
                .setInstanceUuid(id)
                .setGameState(gameState)
                .build()
        )
    }

    override suspend fun notifyInstanceRemoved(gameId: String) {
        instanceSvcStub.removeInstance(
            ServerTracking.InstanceRemovedRequest.newBuilder()
                .setServerName(serverName)
                .setInstanceUuid(gameId)
                .build()
        )
    }

    override suspend fun checkRemoveInstance(gameId: String): Boolean {
        return instanceSvcStub.checkRemoveInstance(
            ServerTracking.InstanceRemovedRequest.newBuilder()
                .setServerName(serverName)
                .setInstanceUuid(gameId)
                .build()
        ).shouldRemove
    }

    override suspend fun recordInstanceChange(player: Player, newGame: String) {
        playerTrackerStub.playerInstanceChange(
            PlayerTrackerOuterClass.PlayerInstanceChangeRequest.newBuilder()
                .setServerName(serverName)
                .setUuid(player.uuid.toString())
                .setInstanceId(newGame)
                .build()
        )
    }

    override suspend fun playerTransfer(player: Player, newGame: String?) {
        playerTrackerStub.playerTransfer(
            PlayerTrackerOuterClass.PlayerTransferRequest.newBuilder()
                .setUuid(player.uuid.toString())
                .setNewServerName(serverName)
                .apply {
                    if (newGame != null) {
                        newInstance = newGame
                    }
                }
                .build()
        )
    }

    override suspend fun queryPlayer(username: String?, uuid: UUID?): PlayerTrackerOuterClass.QueryPlayerResponse {
        return playerTrackerStub.queryPlayer(
            PlayerTrackerOuterClass.PlayerQueryRequest.newBuilder()
                .apply {
                    if (username != null) setUsername(username)
                    if (uuid != null) setUuid(uuid.toString())
                }
                .build()
        )
    }

    override suspend fun addToQueue(player: Player, gameType: CommonTypes.GameType) {
        queueStub.addToQueue(
            Queue.AddToQueueRequest.newBuilder()
                .setPlayerUuid(player.uuid.toString())
                .setGameType(gameType)
                .build()
        )
    }

    override suspend fun removeFromQueue(player: Player) {
        queueStub.removeFromQueue(
            Queue.RemoveFromQueueRequest.newBuilder()
                .setPlayerUuid(player.uuid.toString())
                .build()
        )
    }

    override suspend fun getDestination(player: UUID): String? {
        return queueStub.getDestinationGame(
            Queue.GetDestinationRequest.newBuilder()
                .setPlayerUuid(player.toString())
                .build()
        ).gameId
    }

    override suspend fun sendPrivateMessage(message: Component, sender: CommandSender, recipient: UUID) {
        privateMessageStub.sendMessage(
            VelocityMessage.PrivateMessageRequest.newBuilder()
                .setMessage(miniMessage.serialize(message))
                .setRecipientUuid(recipient.toString())
                .setSenderUsername((sender as? Player)?.username ?: "[Console]")
                .setSenderUuid((sender as? Player)?.uuid?.toString() ?: UUID(0L, 0L).toString())
                .build()
        )
    }

    override suspend fun inviteToParty(partyOwner: UUID, invitee: UUID) {
        partyStub.inviteToParty(
            PartySvc.PartyInviteRequest.newBuilder()
                .setPlayerUuid(invitee.toString())
                .setPartyOwnerUuid(partyOwner.toString())
                .build()
        )
    }

    override suspend fun acceptPartyInvitation(partyOwner: UUID, invitee: UUID) {
        partyStub.acceptInvitation(
            PartySvc.PartyAcceptInviteRequest.newBuilder()
                .setPlayerUuid(invitee.toString())
                .setPartyOwnerUuid(partyOwner.toString())
                .build()
        )
    }

    override suspend fun kickFromParty(partyOwner: UUID, player: UUID) {
        partyStub.removeFromParty(
            PartySvc.PartyRemoveRequest.newBuilder()
                .setPlayerUuid(player.toString())
                .setPartyOwnerUuid(partyOwner.toString())
                .build()
        )
    }

    override suspend fun leaveParty(player: UUID) {
        partyStub.leaveParty(
            PartySvc.PartyLeaveRequest.newBuilder()
                .setPlayerUuid(player.toString())
                .build()
        )
    }

    override suspend fun partyChat(message: String, sender: Player) {
        partyStub.partyChat(
            PartySvc.PartyChatRequest.newBuilder()
                .setPlayerUuid(sender.uuid.toString())
                .setMessage(message)
                .build()
        )
    }

    override suspend fun warpParty(partyOwner: Player, instance: Instance) {
        partyStub.warpParty(
            PartySvc.PartyWarpRequest.newBuilder()
                .setPartyOwnerUuid(partyOwner.uuid.toString())
                .setInstanceUuid(instance.uniqueId.toString())
                .setServerName(serverName)
                .build()
        )
    }

    override suspend fun transferParty(partyOwner: Player, newOwner: UUID) {
        partyStub.transferParty(
            PartySvc.PartyTransferRequest.newBuilder()
                .setPlayerUuid(partyOwner.uuid.toString())
                .setNewOwnerUuid(newOwner.toString())
                .build()
        )
    }

    override suspend fun listPartyMembers(member: UUID): PartySvc.PartyListResponse {
        return partyStub.partyList(
            PartySvc.PartyListRequest.newBuilder()
                .setPlayerUuid(member.toString())
                .build()
        )
    }

    override suspend fun getSongInfo(player: Player): JukeboxOuterClass.PlayerSongQueue {
        return jukeboxStub.getSongInfo(songInfoRequest {
            playerUuid = player.uuid.toString()
        })
    }

    override suspend fun playSong(
        player: Player,
        songName: String,
        queuePosition: Int,
        startTimeInTicks: Int,
        tags: List<String>,
    ): Boolean {
        return jukeboxStub.playSong(playSongRequest {
            this.playerUuid = player.uuid.toString()
            this.songName = songName
            this.queuePosition = queuePosition
            this.startTimeTicks = startTimeInTicks
            tags.forEach { this.tags.add(it) }
        }).startedPlaying
    }

    override suspend fun removeSongByName(player: Player, songName: String) {
        jukeboxStub.removeSong(songRemoveRequest {
            this.playerUuid = player.uuid.toString()
            this.songName = songName
        })
    }

    override suspend fun removeSongByTag(player: Player, matchTags: List<String>) {
        jukeboxStub.removeSongs(batchSongRemoveRequest {
            this.playerUuid = player.uuid.toString()
            matchTags.forEach { this.matchTags.add(it) }
        })
    }

    override suspend fun stopSongAndClearQueue(player: Player) {
        jukeboxStub.stopSong(stopSongRequest {
            this.playerUuid = player.uuid.toString()
        })
    }
}
