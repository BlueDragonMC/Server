package com.bluedragonmc.server

import com.bluedragonmc.server.queue.IPCQueue
import com.bluedragonmc.server.queue.Queue
import com.bluedragonmc.server.queue.TestQueue
import java.io.File

object Environment {
    /**
     * If in a develoment environment, the test queue is used.
     * If inside a Docker container, the IPCQueue is used.
     */
    val queue: Queue = if (File("/server").exists()) IPCQueue else TestQueue()
    val messagingDisabled = queue is TestQueue
    val mongoHostname = if (messagingDisabled) "localhost" else "mongo"

    fun isDev() = messagingDisabled
}