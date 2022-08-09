package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.Environment
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

abstract class Bootstrap(private val requiredEnvironment: KClass<out Environment> = Environment::class) {

    protected val logger: Logger by lazy {
        LoggerFactory.getLogger(this::class.java)
    }

    abstract fun hook(eventNode: EventNode<Event>)

    fun canHook() = requiredEnvironment.isInstance(Environment.current)

}
