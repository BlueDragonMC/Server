package com.bluedragonmc.server.impl

import com.bluedragonmc.api.grpc.*
import com.bluedragonmc.api.grpc.Queue
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.OutgoingRPCHandler
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.listen
import com.bluedragonmc.server.utils.listenAsync
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
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

class OutgoingRPCHandlerImpl(serverAddress: String, serverPort: Int) : OutgoingRPCHandler {

    private lateinit var serverName: String

    private val channel =
        ManagedChannelBuilder.forAddress(serverAddress, serverPort)
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
            Messaging.outgoing.initGame(parent.id, parent.gameType, parent.rpcGameState)

            eventNode.listenAsync<GameStateChangedEvent> { event ->
                Messaging.outgoing.updateGameState(parent.id, event.game.rpcGameState)

                if (event.newState == GameState.ENDING) {
                    MinecraftServer.getSchedulerManager().buildTask {
                        Messaging.IO.launch {
                            Messaging.outgoing.getMarathonLeaderboard(event.game.players.map { it.uuid }, true)
                        }
                    }.delay(Duration.ofSeconds(2)).schedule()
                }
            }

            eventNode.listenAsync<AddEntityToInstanceEvent> { event ->
                val gameId = Game.findGame(event.instance.uuid)?.id
                if (gameId != null) {
                    Messaging.outgoing.recordInstanceChange(event.entity as? Player ?: return@listenAsync, gameId)
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
            }
        }

        override fun deinitialize() {
            Messaging.IO.launch {
                Messaging.outgoing.notifyInstanceRemoved(parent.id)
            }
        }
    }

    private val instanceSvcStub = InstanceServiceGrpcKt.InstanceServiceCoroutineStub(channel)
    private val gameStateSvcStub = GameStateServiceGrpcKt.GameStateServiceCoroutineStub(channel)
    private val privateMessageStub =
        VelocityMessageServiceGrpcKt.VelocityMessageServiceCoroutineStub(channel)
    private val playerTrackerStub = PlayerTrackerGrpcKt.PlayerTrackerCoroutineStub(channel)
    private val queueStub = QueueServiceGrpcKt.QueueServiceCoroutineStub(channel)
    private val partyStub = PartyServiceGrpcKt.PartyServiceCoroutineStub(channel)
    private val jukeboxStub = JukeboxGrpcKt.JukeboxCoroutineStub(channel)

    override fun isConnected(): Boolean {
        return !channel.isShutdown && !channel.isTerminated && ::serverName.isInitialized
    }

    override suspend fun initGameServer(serverName: String) {
        instanceSvcStub.withDeadlineAfter(5, TimeUnit.SECONDS).initGameServer(
            ServerTracking.InitGameServerRequest.newBuilder()
                .setServerName(serverName)
                .build()
        )
        this.serverName = serverName
    }

    override fun onGameCreated(game: Game) {
        game.use(MessagingModule())
    }

    override suspend fun initGame(id: String, gameType: CommonTypes.GameType, gameState: CommonTypes.GameState) {
        instanceSvcStub.withDeadlineAfter(5, TimeUnit.SECONDS).createInstance(
            ServerTracking.InstanceCreatedRequest.newBuilder()
                .setInstanceUuid(id)
                .setGameType(gameType)
                .setServerName(serverName)
                .setGameState(gameState)
                .build()
        )
    }

    override suspend fun updateGameState(id: String, gameState: CommonTypes.GameState) {
        gameStateSvcStub.withDeadlineAfter(5, TimeUnit.SECONDS).updateGameState(
            ServerTracking.GameStateUpdateRequest.newBuilder()
                .setServerName(serverName)
                .setInstanceUuid(id)
                .setGameState(gameState)
                .build()
        )
    }

    override suspend fun notifyInstanceRemoved(gameId: String) {
        instanceSvcStub.withDeadlineAfter(5, TimeUnit.SECONDS).removeInstance(
            ServerTracking.InstanceRemovedRequest.newBuilder()
                .setServerName(serverName)
                .setInstanceUuid(gameId)
                .build()
        )
    }

    override suspend fun checkRemoveInstance(gameId: String): Boolean {
        return instanceSvcStub.withDeadlineAfter(5, TimeUnit.SECONDS).checkRemoveInstance(
            ServerTracking.InstanceRemovedRequest.newBuilder()
                .setServerName(serverName)
                .setInstanceUuid(gameId)
                .build()
        ).shouldRemove
    }

    override suspend fun recordInstanceChange(player: Player, newGame: String) {
        playerTrackerStub.withDeadlineAfter(5, TimeUnit.SECONDS).playerInstanceChange(
            PlayerTrackerOuterClass.PlayerInstanceChangeRequest.newBuilder()
                .setServerName(serverName)
                .setUuid(player.uuid.toString())
                .setInstanceId(newGame)
                .build()
        )
    }

    override suspend fun playerTransfer(player: Player, newGame: String?) {
        playerTrackerStub.withDeadlineAfter(5, TimeUnit.SECONDS).playerTransfer(
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
        return playerTrackerStub.withDeadlineAfter(5, TimeUnit.SECONDS).queryPlayer(
            PlayerTrackerOuterClass.PlayerQueryRequest.newBuilder()
                .apply {
                    if (username != null) setUsername(username)
                    if (uuid != null) setUuid(uuid.toString())
                }
                .build()
        )
    }

    override suspend fun addToQueue(player: Player, gameType: CommonTypes.GameType) {
        queueStub.withDeadlineAfter(5, TimeUnit.SECONDS).addToQueue(
            Queue.AddToQueueRequest.newBuilder()
                .setPlayerUuid(player.uuid.toString())
                .setGameType(gameType)
                .build()
        )
    }

    override suspend fun removeFromQueue(player: Player) {
        queueStub.withDeadlineAfter(5, TimeUnit.SECONDS).removeFromQueue(
            Queue.RemoveFromQueueRequest.newBuilder()
                .setPlayerUuid(player.uuid.toString())
                .build()
        )
    }

    override suspend fun getDestination(player: UUID): String? {
        return queueStub.withDeadlineAfter(5, TimeUnit.SECONDS).getDestinationGame(
            Queue.GetDestinationRequest.newBuilder()
                .setPlayerUuid(player.toString())
                .build()
        ).gameId
    }

    override suspend fun sendPrivateMessage(message: Component, sender: CommandSender, recipient: UUID) {
        privateMessageStub.withDeadlineAfter(5, TimeUnit.SECONDS).sendMessage(
            VelocityMessage.PrivateMessageRequest.newBuilder()
                .setMessage(miniMessage.serialize(message))
                .setRecipientUuid(recipient.toString())
                .setSenderUsername((sender as? Player)?.username ?: "[Console]")
                .setSenderUuid((sender as? Player)?.uuid?.toString() ?: UUID(0L, 0L).toString())
                .build()
        )
    }

    override suspend fun inviteToParty(partyOwner: UUID, invitee: UUID) {
        partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).inviteToParty(
            PartySvc.PartyInviteRequest.newBuilder()
                .setPlayerUuid(invitee.toString())
                .setPartyOwnerUuid(partyOwner.toString())
                .build()
        )
    }

    override suspend fun acceptPartyInvitation(partyOwner: UUID, invitee: UUID) {
        partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).acceptInvitation(
            PartySvc.PartyAcceptInviteRequest.newBuilder()
                .setPlayerUuid(invitee.toString())
                .setPartyOwnerUuid(partyOwner.toString())
                .build()
        )
    }

    override suspend fun kickFromParty(partyOwner: UUID, player: UUID) {
        partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).removeFromParty(
            PartySvc.PartyRemoveRequest.newBuilder()
                .setPlayerUuid(player.toString())
                .setPartyOwnerUuid(partyOwner.toString())
                .build()
        )
    }

    override suspend fun leaveParty(player: UUID) {
        partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).leaveParty(
            PartySvc.PartyLeaveRequest.newBuilder()
                .setPlayerUuid(player.toString())
                .build()
        )
    }

    override suspend fun partyChat(message: String, sender: Player) {
        partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).partyChat(
            PartySvc.PartyChatRequest.newBuilder()
                .setPlayerUuid(sender.uuid.toString())
                .setMessage(message)
                .build()
        )
    }

    override suspend fun warpParty(partyOwner: Player, gameId: String) {
        partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).warpParty(
            PartySvc.PartyWarpRequest.newBuilder()
                .setPartyOwnerUuid(partyOwner.uuid.toString())
                .setInstanceUuid(gameId)
                .setServerName(serverName)
                .build()
        )
    }

    override suspend fun transferParty(partyOwner: Player, newOwner: UUID) {
        partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).transferParty(
            PartySvc.PartyTransferRequest.newBuilder()
                .setPlayerUuid(partyOwner.uuid.toString())
                .setNewOwnerUuid(newOwner.toString())
                .build()
        )
    }

    override suspend fun listPartyMembers(member: UUID): PartySvc.PartyListResponse {
        return partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).partyList(
            PartySvc.PartyListRequest.newBuilder()
                .setPlayerUuid(member.toString())
                .build()
        )
    }

    override suspend fun startMarathon(player: UUID, durationMs: Int) {
        partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).startMarathon(
            PartySvc.StartMarathonRequest.newBuilder()
                .setPlayerUuid(player.toString())
                .setDurationMs(durationMs)
                .build()
        )
    }

    override suspend fun endMarathon(player: UUID) {
        partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).stopMarathon(
            PartySvc.StopMarathonRequest.newBuilder()
                .setPlayerUuid(player.toString())
                .build()
        )
    }

    override suspend fun getMarathonLeaderboard(players: Collection<UUID>, silent: Boolean) {
        partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).getMarathonLeaderboard(
            PartySvc.MarathonLeaderboardRequest.newBuilder()
                .addAllPlayerUuids(players.map { it.toString() })
                .setSilent(silent)
                .build()
        )
    }

    override suspend fun recordCoinAward(player: UUID, coins: Int, gameId: String) {
        partyStub.withDeadlineAfter(5, TimeUnit.SECONDS).recordCoinAward(
            PartySvc.RecordCoinAwardRequest.newBuilder()
                .setPlayerUuid(player.toString())
                .setCoins(coins)
                .setGameId(gameId)
                .build()
        )
    }

    override suspend fun getSongInfo(player: Player): JukeboxOuterClass.PlayerSongQueue {
        return jukeboxStub.withDeadlineAfter(5, TimeUnit.SECONDS).getSongQueue(getSongQueueRequest {
            playerUuid = player.uuid.toString()
        })
    }

    override suspend fun setSongInfo(
        player: Player,
        songQueue: JukeboxOuterClass.PlayerSongQueue
    ) {
        jukeboxStub.withDeadlineAfter(5, TimeUnit.SECONDS).setSongQueue(setSongQueueRequest {
            playerUuid = player.uuid.toString()
            queue = songQueue
        })
    }
}
