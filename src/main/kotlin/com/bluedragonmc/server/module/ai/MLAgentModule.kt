package com.bluedragonmc.server.module.ai

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode

class MLAgentModule : GameModule() {

    private val agents = mutableListOf<MLAgent>()

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        agents.add(MLAgent(parent.getInstance()))
        agents.add(MLAgent(parent.getInstance()))
    }

    override fun deinitialize() {
        agents.forEach { it.disconnect() }
        agents.clear()
    }
}