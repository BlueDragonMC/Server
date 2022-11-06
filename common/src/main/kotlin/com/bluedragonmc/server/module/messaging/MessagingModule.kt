package com.bluedragonmc.server.module.messaging

import com.bluedragonmc.api.grpc.*
import com.bluedragonmc.api.grpc.ServerSyncRequestKt.runningInstance
import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.module.messaging.MessagingModule.Stubs.gameStateSvcStub
import com.bluedragonmc.server.module.messaging.MessagingModule.Stubs.instanceSvcStub
import com.bluedragonmc.server.module.messaging.MessagingModule.Stubs.serverTrackingStub
import com.bluedragonmc.server.utils.miniMessage
import com.google.protobuf.Empty
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.Consumer
import kotlin.concurrent.timer
import kotlin.system.exitProcess

@DependsOn(InstanceModule::class)
class MessagingModule : GameModule() {

    object Stubs {
        private val channel by lazy {
            ManagedChannelBuilder.forAddress(
                Environment.current.puffinHostname, 50051
            ).usePlaintext().build()
        }

        val serverTrackingStub by lazy {
            ServerTrackerServiceGrpcKt.ServerTrackerServiceCoroutineStub(channel)
        }

        val instanceSvcStub by lazy {
            InstanceServiceGrpcKt.InstanceServiceCoroutineStub(channel)
        }

        val gameStateSvcStub by lazy {
            GameStateServiceGrpcKt.GameStateServiceCoroutineStub(channel)
        }

        val playerTrackerStub by lazy {
            PlayerTrackerGrpcKt.PlayerTrackerCoroutineStub(channel)
        }

        val queueStub by lazy {
            QueueServiceGrpcKt.QueueServiceCoroutineStub(channel)
        }

        val partyStub by lazy {
            PartyServiceGrpcKt.PartyServiceCoroutineStub(channel)
        }
    }

    companion object {

        private val logger = LoggerFactory.getLogger(Companion::class.java)

        lateinit var serverName: String

        fun findPlayer(uuid: UUID) = MinecraftServer.getConnectionManager().getPlayer(uuid)

        private val ZERO_UUID = UUID(0L, 0L)

        private val serverNameWaitingActions = mutableListOf<Consumer<String>>()

        fun getServerName(consumer: Consumer<String>) {
            if (Companion::serverName.isInitialized) consumer.accept(serverName)
            else serverNameWaitingActions.add(consumer)
        }

        init {
            DatabaseModule.IO.launch {
                try {
                    serverName = Environment.current.getServerName()
                } catch (e: Throwable) {
                    e.printStackTrace()
                    logger.error("Severe error: failed to gather contaier ID from Environment.")
                    exitProcess(1)
                }
                serverTrackingStub.initGameServer(initGameServerRequest {
                    serverName = this@Companion.serverName
                })
                logger.info("Published ping message.")
                serverNameWaitingActions.forEach { it.accept(serverName) }
                serverNameWaitingActions.clear()
            }
            timer("server-sync", daemon = true, initialDelay = 30_000, period = 30_000) {
                // Every 30 seconds, send a synchronization message
                DatabaseModule.IO.launch {
                    serverTrackingStub.serverSync(serverSyncRequest {
                        serverName = this@Companion.serverName
                        instances += Game.games.mapNotNull {
                            runningInstance {
                                instanceUuid = it.instanceId.toString()
                                gameType = it.gameType
                                gameState = it.rpcGameState
                            }
                        }
                    })
                }
            }
        }
    }

    lateinit var instanceId: UUID
    private lateinit var parent: Game

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        instanceId = parent.getInstance().uniqueId
        if (Environment.current.messagingDisabled) return
        getServerName {
            runBlocking {
                instanceSvcStub.createInstance(instanceCreatedRequest {
                    serverName = it
                    instanceUuid = instanceId.toString()
                    gameType = parent.gameType
                })
            }
        }
        eventNode.addListener(GameStateChangedEvent::class.java) { event ->
            DatabaseModule.IO.launch {
                gameStateSvcStub.updateGameState(gameStateUpdateRequest {
                    serverName = MessagingModule.serverName
                    instanceUuid = event.game.instanceId.toString()
                    gameState = event.game.rpcGameState
                })
            }
        }
    }

    override fun deinitialize() {
        getServerName {
            runBlocking {
                instanceSvcStub.removeInstance(instanceRemovedRequest {
                    serverName = it
                    instanceUuid = instanceId.toString()
                })
            }
        }
    }

    fun refreshState() {
        DatabaseModule.IO.launch {
            gameStateSvcStub.updateGameState(gameStateUpdateRequest {
                serverName = MessagingModule.serverName
                instanceUuid = instanceId.toString()
                gameState = parent.rpcGameState
            })
        }
    }

    inner class GameClientService : GsClientServiceGrpcKt.GsClientServiceCoroutineImplBase() {
        override suspend fun createInstance(request: GsClient.CreateInstanceRequest): CommonTypes.GameState {
            val game = Environment.current.queue.createInstance(request)
            return game?.rpcGameState ?: gameState {
                gameState = CommonTypes.EnumGameState.ERROR
                joinable = false
                openSlots = 0
            }
        }

        override suspend fun sendChat(request: GsClient.SendChatRequest): Empty {
            val target = if (request.playerUuid == ZERO_UUID.toString())
                MinecraftServer.getCommandManager().consoleSender
            else findPlayer(UUID.fromString(request.playerUuid))
                ?: return Empty.getDefaultInstance()

            val msg = miniMessage.deserialize(request.message)
            when (request.chatType) {
                GsClient.SendChatRequest.ChatType.CHAT -> target.sendMessage(msg)
                GsClient.SendChatRequest.ChatType.ACTION_BAR -> target.sendActionBar(msg)
                else -> {}
            }
            return Empty.getDefaultInstance()
        }
    }
}