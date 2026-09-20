package com.bluedragonmc.server

import com.bluedragonmc.server.module.GameModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventListener
import net.minestom.server.event.EventNode

internal class SingleEventModule<T : Event>(private val listener: EventListener<out T>) : GameModule() {
    override fun initialize(
        parent: ModuleHolder,
        eventNode: EventNode<Event>,
    ) {
        eventNode.addListener(listener)
    }

    override fun toString(): String {
        return "SingleEventModule(eventType=${listener.eventType().simpleName})"
    }
}
