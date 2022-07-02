package com.bluedragonmc.server.module

import com.bluedragonmc.server.Game
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class GameModule {

    var eventNode: EventNode<Event>? = null
    abstract fun initialize(parent: Game, eventNode: EventNode<Event>)
    open fun deinitialize() {}

    val logger: Logger
        get() = LoggerFactory.getLogger(javaClass)

}