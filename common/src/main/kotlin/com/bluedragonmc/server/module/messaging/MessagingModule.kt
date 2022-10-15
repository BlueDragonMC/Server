package com.bluedragonmc.server.module.messaging

import com.bluedragonmc.messages.*
import com.bluedragonmc.messagingsystem.AMQPClient
import com.bluedragonmc.messagingsystem.message.Message
import com.bluedragonmc.messagingsystem.message.RPCErrorMessage
import com.bluedragonmc.server.Environment
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.database.DatabaseModule
import com.bluedragonmc.server.module.instance.InstanceModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.miniMessage
import kotlinx.coroutines.launch
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.title.TitlePart
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.Consumer
import kotlin.concurrent.timer
import kotlin.reflect.KClass
import kotlin.system.exitProcess

@DependsOn(InstanceModule::class)
class MessagingModule : GameModule() {

    companion object {

        private val logger = LoggerFactory.getLogger(Companion::class.java)

        lateinit var containerId: UUID

        private val client: AMQPClient by lazy {
            AMQPClient(polymorphicModuleBuilder = polymorphicModuleBuilder)
        }

        fun findPlayer(uuid: UUID) = MinecraftServer.getConnectionManager().getPlayer(uuid)
        fun UUID.asPlayer() = findPlayer(this)

        fun publish(message: Message) {
            if (!Environment.current.messagingDisabled) client.publish(message)
        }

        suspend fun send(message: Message): Message {
            return if (!Environment.current.messagingDisabled) client.publishAndReceive(message)
            else RPCErrorMessage("Messaging disabled")
        }

        fun <T : Message> subscribe(type: KClass<T>, listener: (T) -> Unit) {
            if (!Environment.current.messagingDisabled) client.subscribe(type, listener)
        }

        fun <T : Message> consume(type: KClass<T>, listener: (T) -> Message) {
            if (!Environment.current.messagingDisabled) client.subscribeRPC(type, listener)
        }

        private val ZERO_UUID = UUID(0L, 0L)

        private val containerIdWaitingActions = mutableListOf<Consumer<UUID>>()

        fun getContainerId(consumer: Consumer<UUID>) {
            if (Companion::containerId.isInitialized) consumer.accept(containerId)
            else containerIdWaitingActions.add(consumer)
        }

        init {
            DatabaseModule.IO.launch {
                try {
                    containerId = Environment.current.getContainerId()
                } catch (e: Throwable) {
                    e.printStackTrace()
                    logger.error("Severe error: failed to gather contaier ID from Environment.")
                    exitProcess(1)
                }
                publish(PingMessage(containerId, emptyMap()))
                logger.info("Published ping message.")
                containerIdWaitingActions.forEach { it.accept(containerId) }
                containerIdWaitingActions.clear()
            }
            subscribe(SendChatMessage::class) { message ->
                val target = if (message.targetPlayer == ZERO_UUID) MinecraftServer.getCommandManager().consoleSender
                else message.targetPlayer.asPlayer() ?: return@subscribe
                val msg = miniMessage.deserialize(message.message)
                when (message.type) {
                    ChatType.CHAT -> target.sendMessage(msg)
                    ChatType.ACTION_BAR -> target.sendActionBar(msg)
                    ChatType.TITLE -> target.sendTitlePart(TitlePart.TITLE, msg)
                    ChatType.SUBTITLE -> target.sendTitlePart(TitlePart.SUBTITLE, msg)
                    ChatType.SOUND -> target.playSound(Sound.sound(Key.key(message.message),
                        Sound.Source.PLAYER,
                        1f,
                        1f))
                }
            }
            timer("server-sync", daemon = true, initialDelay = 30_000, period = 30_000) {
                // Every 30 seconds, send a synchronization message
                publish(ServerSyncMessage(containerId, Game.games.mapNotNull {
                    RunningGameInfo(it.instanceId ?: return@mapNotNull null,
                        GameType(it.name, it.mode, it.mapName),
                        getGameStateUpdateMessage(it, it.state))
                }))
            }
        }

        fun getGameStateUpdateMessage(game: Game, state: GameState) = game.run {
            GameStateUpdateMessage(instanceId!!, if (state.canPlayersJoin) maxPlayers - players.size else 0)
        }
    }

    lateinit var instanceId: UUID
    private lateinit var parent: Game

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        instanceId = parent.getInstance().uniqueId
        getContainerId { containerId ->
            publish(
                NotifyInstanceCreatedMessage(containerId, instanceId, GameType(parent.name, null, parent.mapName))
            )
        }
        eventNode.addListener(GameStateChangedEvent::class.java) { event ->
            publish(getGameStateUpdateMessage(parent, event.newState))
        }
    }

    override fun deinitialize() {
        getContainerId { containerId -> publish(NotifyInstanceRemovedMessage(containerId, instanceId)) }
    }

    fun refreshState() {
        publish(getGameStateUpdateMessage(parent, parent.state))
    }
}