package com.bluedragonmc.server

import java.util.*

abstract class Environment {

    companion object {

        lateinit var current: Environment

        fun setEnvironment(env: Environment) {
            if (!::current.isInitialized) {
                current = env
            } else throw IllegalStateException("Tried to override current Environment")
        }
    }

    abstract val queue: com.bluedragonmc.server.api.Queue
    abstract val messagingDisabled: Boolean
    abstract val mongoHostname: String
    abstract val gameClasses: Collection<String>
    abstract suspend fun getContainerId(): UUID
}