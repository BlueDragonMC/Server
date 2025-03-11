package com.bluedragonmc.server.bootstrap

import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.network.packet.client.play.ClientPickItemFromBlockPacket
import com.bluedragonmc.server.module.vanilla.PickItemModule
import net.minestom.server.network.packet.client.play.ClientPickItemFromEntityPacket

object PickItemHandler : Bootstrap(EnvType.ANY) {
    override fun hook(eventNode: EventNode<Event>) {
        MinecraftServer.getPacketListenerManager()
            .setPlayListener(ClientPickItemFromBlockPacket::class.java) { packet, player ->
                MinecraftServer.getGlobalEventHandler()
                    .call(PickItemModule.PickItemEvent.Block(player, packet.pos, packet.includeData))
            }
        MinecraftServer.getPacketListenerManager()
            .setPlayListener(ClientPickItemFromEntityPacket::class.java) { packet, player ->
                MinecraftServer.getGlobalEventHandler().call(
                    PickItemModule.PickItemEvent.Entity(
                        player,
                        player.instance.getEntityById(packet.entityId),
                        packet.includeData
                    )
                )
            }
    }
}