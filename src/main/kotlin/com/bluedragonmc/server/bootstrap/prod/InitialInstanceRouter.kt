package com.bluedragonmc.server.bootstrap.prod

import com.bluedragonmc.messages.SendPlayerToInstanceMessage
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.bootstrap.Bootstrap
import com.bluedragonmc.server.lobby
import com.bluedragonmc.server.module.messaging.MessagingModule
import com.bluedragonmc.server.queue.ProductionEnvironment
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.instance.Instance
import java.time.Duration
import java.util.*

object InitialInstanceRouter : Bootstrap(ProductionEnvironment::class) {

    private val futureInstances: Cache<UUID, Instance> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(1))
        .expireAfterAccess(Duration.ZERO)
        .build()

    override fun hook(eventNode: EventNode<Event>) {
        // Send players to the instance they are supposed to join instead of the lobby,
        // if a SendPlayerToInstanceMessage was received before they joined.
        MessagingModule.subscribe(SendPlayerToInstanceMessage::class) { message ->
            val instance = MinecraftServer.getInstanceManager().getInstance(message.instance)
            if (instance != null && MinecraftServer.getConnectionManager().getPlayer(message.player) == null) {
                futureInstances.put(message.player, instance)
            }
        }

        // Make players spawn in the correct instance
        MinecraftServer.getGlobalEventHandler().addListener(PlayerLoginEvent::class.java) { event ->
            val instance = futureInstances.getIfPresent(event.player.uuid) ?: lobby.getInstance()
            val game = Game.findGame(instance.uniqueId)
            if (event.player.displayName == null)
                event.player.displayName = Component.text(
                    event.player.username,
                    BRAND_COLOR_PRIMARY_1 // The default color that appears before group data is loaded
                )
            event.player.sendMessage(Component.translatable("global.instance.placing", NamedTextColor.DARK_GRAY, Component.text(instance.uniqueId.toString())))
            event.setSpawningInstance(instance)
            game?.players?.add(event.player)
        }
    }
}