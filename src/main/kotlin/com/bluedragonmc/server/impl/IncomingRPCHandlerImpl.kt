package com.bluedragonmc.server.impl

import com.bluedragonmc.api.grpc.*
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.IncomingRPCHandler
import com.bluedragonmc.server.utils.miniMessage
import com.google.protobuf.Empty
import io.grpc.ServerBuilder
import net.minestom.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit

class IncomingRPCHandlerImpl : IncomingRPCHandler {

    private val server = ServerBuilder.forPort(50051)
        .addService(GameClientService())
        .addService(PlayerHolderService())
        .build()

    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        server.start()
        logger.info("gRPC server started on port 50051")
    }

    override fun isConnected() = !server.isShutdown && !server.isTerminated

    init {
        MinecraftServer.getSchedulerManager().buildShutdownTask {
            server.shutdown()
            server.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    class GameClientService : GsClientServiceGrpcKt.GsClientServiceCoroutineImplBase() {

        private val logger = LoggerFactory.getLogger(this::class.java)

        private val ZERO_UUID = UUID(0L, 0L)
        private val ZERO_UUID_STRING = ZERO_UUID.toString()

        override suspend fun createInstance(request: GsClient.CreateInstanceRequest): GsClient.CreateInstanceResponse {
            val game = runCatching {
                Environment.queue.createInstance(request)
            }.onFailure {
                logger.error("Failed to create instance from request: $request")
                it.printStackTrace()
            }.getOrElse { null }
            val state = game?.rpcGameState ?: gameState {
                gameState = CommonTypes.EnumGameState.ERROR
                joinable = false
                openSlots = 0
            }
            return createInstanceResponse {
                this.gameState = state
                this.success = game != null
                game?.id?.let { this.instanceUuid = it }
            }
        }

        override suspend fun sendChat(request: GsClient.SendChatRequest): Empty {
            val target = if (request.playerUuid == ZERO_UUID_STRING)
                MinecraftServer.getCommandManager().consoleSender
            else MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(UUID.fromString(request.playerUuid))
                ?: return Empty.getDefaultInstance()

            val msg = miniMessage.deserialize(request.message)
            when (request.chatType) {
                GsClient.SendChatRequest.ChatType.CHAT -> target.sendMessage(msg)
                GsClient.SendChatRequest.ChatType.ACTION_BAR -> target.sendActionBar(msg)
                else -> {}
            }
            return Empty.getDefaultInstance()
        }

        override suspend fun getInstances(request: Empty): GsClient.GetInstancesResponse {
            return getInstancesResponse {
                Game.games.forEach { game ->
                    instances += GetInstancesResponseKt.runningInstance {
                        this.gameState = game.rpcGameState
                        this.instanceUuid = game.id
                        this.gameType = game.gameType
                    }
                }
            }
        }
    }

    class PlayerHolderService : PlayerHolderGrpcKt.PlayerHolderCoroutineImplBase() {
        override suspend fun sendPlayer(request: PlayerHolderOuterClass.SendPlayerRequest): PlayerHolderOuterClass.SendPlayerResponse {
            Environment.queue.sendPlayer(request)
            return sendPlayerResponse {
                successes += PlayerHolderOuterClass.SendPlayerResponse.SuccessFlags.SET_INSTANCE
            }
        }

        override suspend fun getPlayers(request: Empty): PlayerHolderOuterClass.GetPlayersResponse {
            return getPlayersResponse {
                MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
                    players += GetPlayersResponseKt.connectedPlayer {
                        this.serverName = Environment.getServerName()
                        this.uuid = player.uuid.toString()
                        this.username = player.username
                    }
                }
            }
        }
    }
}