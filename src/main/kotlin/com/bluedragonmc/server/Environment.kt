package com.bluedragonmc.server

import com.bluedragonmc.server.queue.IPCQueue
import com.bluedragonmc.server.queue.Queue
import com.bluedragonmc.server.queue.TestQueue
import java.io.File

abstract class Environment {

    companion object {
        val current by lazy {
            if (isDev()) DevelopmentEnvironment() else ProductionEnvironment()
        }

        private fun isDev() = !File("/server").exists()
    }

    abstract val queue: Queue
    abstract val messagingDisabled: Boolean
    abstract val mongoHostname: String

    class DevelopmentEnvironment : Environment() {
        override val queue: Queue = TestQueue()
        override val messagingDisabled: Boolean = true
        override val mongoHostname: String = "localhost"
    }

    class ProductionEnvironment : Environment() {
        override val queue: Queue = IPCQueue
        override val messagingDisabled: Boolean = false
        override val mongoHostname: String = "mongo"
    }
}