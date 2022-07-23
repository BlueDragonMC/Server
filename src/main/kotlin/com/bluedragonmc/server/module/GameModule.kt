package com.bluedragonmc.server.module

import com.bluedragonmc.server.Game
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

abstract class GameModule {

    open val dependencies = listOf<KClass<out GameModule>>()
    open val eventPriority = 0

    var eventNode: EventNode<Event>? = null

    abstract fun initialize(parent: Game, eventNode: EventNode<Event>)
    open fun deinitialize() {}

    val logger: Logger
        get() = LoggerFactory.getLogger(javaClass)

}