package com.bluedragonmc.server.module

import com.bluedragonmc.server.Game
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.full.findAnnotation

abstract class GameModule {

    open val eventPriority = 0

    lateinit var eventNode: EventNode<Event>

    abstract fun initialize(parent: Game, eventNode: EventNode<Event>)
    open fun deinitialize() {}

    val logger: Logger by lazy {
        LoggerFactory.getLogger(javaClass)
    }

    open fun getDependencies() = this::class.findAnnotation<DependsOn>()?.dependencies ?: emptyArray()

}