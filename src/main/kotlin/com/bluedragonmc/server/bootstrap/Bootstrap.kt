package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.api.Environment
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class Bootstrap(private val envType: EnvType = EnvType.ANY) {

    enum class EnvType {
        PRODUCTION, DEVELOPMENT, ANY
    }

    protected val logger: Logger by lazy {
        LoggerFactory.getLogger(this::class.java)
    }

    abstract fun hook(eventNode: EventNode<Event>)

    fun canHook(): Boolean {
        return envType == EnvType.ANY || if (Environment.current.isDev) {
            envType == EnvType.DEVELOPMENT
        } else {
            envType == EnvType.PRODUCTION
        }
    }

}
