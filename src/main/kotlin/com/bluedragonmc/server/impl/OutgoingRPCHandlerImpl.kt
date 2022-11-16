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

class OutgoingRPCHandlerImpl(serverAddress: String) : OutgoingRPCHandler {

    private lateinit var serverName: String

    private val channel =
        ManagedChannelBuilder.forAddress(serverAddress, 50051)
            .defaultLoadBalancingPolicy("round_robin")
            .usePlaintext()
            .enableRetry()
            .build()

    @DependsOn(InstanceModule::class)
    class MessagingModule : GameModule() {

        private lateinit var parent: Game
        private lateinit var instanceId: UUID

        override fun initialize(parent: Game, eventNode: EventNode<Event>): Unit = runBlocking {
            this@MessagingModule.parent = parent
            instanceId = parent.getInstance().uniqueId
            Messaging.outgoing.initInstance(parent.getInstance(), parent.gameType)

            eventNode.listenSuspend<GameStateChangedEvent> { event ->
                Messaging.outgoing.updateGameState(parent.getInstance(), event.rpcGameState)
            }

            eventNode.listenSuspend<AddEntityToInstanceEvent> { event ->
                Messaging.outgoing.recordInstanceChange(
                    event.entity as? Player ?: return@listenSuspend,
                    event.instance
                )
            }

            eventNode.listen<PlayerSpawnEvent> {
                MinecraftServer.getSchedulerManager().scheduleNextTick {
                    Messaging.IO.launch {
                        Messaging.outgoing.updateGameState(parent.getInstance(), parent.rpcGameState)
                    }
                }
            }

            eventNode.listen<PlayerDisconnectEvent> {
                MinecraftServer.getSchedulerManager().scheduleNextTick {
                    Messaging.IO.launch {
                        Messaging.outgoing.updateGameState(parent.getInstance(), parent.rpcGameState)
                    }
                }
            }
        }

        override fun deinitialize(): Unit = runBlocking {
            Messaging.outgoing.notifyInstanceRemoved(instanceId)
        }
    }

    private val instanceSvcStub = InstanceServiceGrpcKt.InstanceServiceCoroutineStub(channel)
    private val gameStateSvcStub = GameStateServiceGrpcKt.GameStateServiceCoroutineStub(channel)
    private val privateMessageStub = VelocityMessageServiceGrpcKt.VelocityMessageServiceCoroutineStub(channel)
    private val playerTrackerStub = PlayerTrackerGrpcKt.PlayerTrackerCoroutineStub(channel)
    private val queueStub = QueueServiceGrpcKt.QueueServiceCoroutineStub(channel)
    private val partyStub = PartyServiceGrpcKt.PartyServiceCoroutineStub(channel)

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

    override suspend fun initInstance(instance: Instance, gameType: CommonTypes.GameType) {
        instanceSvcStub.createInstance(
            ServerTracking.InstanceCreatedRequest.newBuilder()
                .setInstanceUuid(instance.uniqueId.toString())
                .setGameType(gameType)
                .setServerName(serverName)
                .build()
        )
    }

    override suspend fun updateGameState(instance: Instance, gameState: CommonTypes.GameState) {
        gameStateSvcStub.updateGameState(
            ServerTracking.GameStateUpdateRequest.newBuilder()
                .setServerName(serverName)
                .setInstanceUuid(instance.uniqueId.toString())
                .setGameState(gameState)
                .build()
        )
    }

    override suspend fun notifyInstanceRemoved(instanceId: UUID) {
        instanceSvcStub.removeInstance(
            ServerTracking.InstanceRemovedRequest.newBuilder()
                .setServerName(serverName)
                .setInstanceUuid(instanceId.toString())
                .build()
        )
    }

    override suspend fun recordInstanceChange(player: Player, newInstance: Instance) {
        playerTrackerStub.playerInstanceChange(
            PlayerTrackerOuterClass.PlayerInstanceChangeRequest.newBuilder()
                .setServerName(serverName)
                .setUuid(player.uuid.toString())
                .setInstanceId(newInstance.uniqueId.toString())
                .build()
        )
    }

    override suspend fun playerTransfer(player: Player, newInstance: Instance?) {
        playerTrackerStub.playerTransfer(
            PlayerTrackerOuterClass.PlayerTransferRequest.newBuilder()
                .setUuid(player.uuid.toString())
                .setNewServerName(serverName)
                .apply {
                    if (newInstance != null) {
                        setNewInstance(newInstance.uniqueId.toString())
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
                .setPartyOwnerUuid(partyOwner.toString())
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
}