package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.block.SignHandler
import com.bluedragonmc.server.block.SkullHandler
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

object GlobalBlockHandlers : Bootstrap() {
    override fun hook(eventNode: EventNode<Event>) {
        MinecraftServer.getBlockManager().registerHandler("minecraft:sign", ::SignHandler)
        MinecraftServer.getBlockManager().registerHandler("minecraft:skull", ::SkullHandler)
    }
}