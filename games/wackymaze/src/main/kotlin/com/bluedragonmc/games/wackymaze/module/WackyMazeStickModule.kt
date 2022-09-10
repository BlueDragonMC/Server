package com.bluedragonmc.games.wackymaze.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.ItemUtils
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

class WackyMazeStickModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(GameStartEvent::class.java) {
            parent.players.forEach { player ->
                player.inventory.setItemStack(0, ItemUtils.knockbackStick(10, player))
            }
        }
    }
}