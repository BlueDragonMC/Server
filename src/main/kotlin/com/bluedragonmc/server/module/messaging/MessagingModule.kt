package com.bluedragonmc.server.module.messaging

import com.bluedragonmc.messages.*
import com.bluedragonmc.messagingsystem.AMQPClient
import com.bluedragonmc.messagingsystem.message.Message
import com.bluedragonmc.messagingsystem.message.RPCErrorMessage
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.messagingDisabled
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.TitlePart
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.util.*
import kotlin.reflect.KClass

class MessagingModule : GameModule() {

    companion object {
        // TODO for testing only, we are using a random UUID as the container's ID.
        val containerId: UUID = UUID.randomUUID() //UUID.fromString(System.getenv("container_id"))
        private val client: AMQPClient by lazy {
            AMQPClient(polymorphicModuleBuilder = polymorphicModuleBuilder)
        }

        fun findPlayer(uuid: UUID) = MinecraftServer.getConnectionManager().getPlayer(uuid)
        fun UUID.asPlayer() = findPlayer(this)

        fun publish(message: Message) {
            if(!messagingDisabled) client.publish(message)
        }

        suspend fun send(message: Message): Message {
            return if(!messagingDisabled) client.publishAndReceive(message)
            else RPCErrorMessage("Messaging disabled")
        }

        fun <T : Message> subscribe(type: KClass<T>, listener: (T) -> Unit) {
            if(!messagingDisabled) client.subscribe(type, listener)
        }
        fun <T : Message> consume(type: KClass<T>, listener: (T) -> Message) {
            if(!messagingDisabled) client.subscribeRPC(type, listener)
        }

        init {
            publish(
                PingMessage(
                    containerId, mapOf(
                        "initializedAt" to System.currentTimeMillis().toString()
                    )
                )
            )
            println("Published startup ping")
            subscribe(SendChatMessage::class) { message ->
                val player = message.targetPlayer.asPlayer() ?: return@subscribe
                val msg = MiniMessage.miniMessage().deserialize(message.message)
                when (message.type) {
                    ChatType.CHAT -> player.sendMessage(msg)
                    ChatType.ACTION_BAR -> player.sendActionBar(msg)
                    ChatType.TITLE -> player.sendTitlePart(TitlePart.TITLE, msg)
                    ChatType.SUBTITLE -> player.sendTitlePart(TitlePart.SUBTITLE, msg)
                    ChatType.SOUND -> player.playSound(
                        Sound.sound(
                            Key.key(message.message), Sound.Source.PLAYER, 1f, 1f
                        )
                    )
                }
            }
        }
    }

    lateinit var instanceId: UUID

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        instanceId = parent.getInstance().uniqueId
        publish(
            NotifyInstanceCreatedMessage(
                containerId, instanceId, GameType(parent.name, null, parent.mapName)
            )
        )
    }

    override fun deinitialize() {
        publish(NotifyInstanceRemovedMessage(containerId, instanceId))
    }
}