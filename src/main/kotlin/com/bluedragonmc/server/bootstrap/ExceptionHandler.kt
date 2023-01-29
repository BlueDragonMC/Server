package com.bluedragonmc.server.bootstrap

import com.bluedragonmc.server.model.EventLog
import com.bluedragonmc.server.model.Severity
import com.bluedragonmc.server.service.Database
import com.google.common.util.concurrent.RateLimiter
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import java.util.concurrent.TimeUnit

object ExceptionHandler : Bootstrap() {

    private val rateLimiter = RateLimiter.create(2.0)

    override fun hook(eventNode: EventNode<Event>) {
        MinecraftServer.getExceptionManager().setExceptionHandler { throwable ->
            throwable.printStackTrace()
            Database.IO.launch {
                if (rateLimiter.tryAcquire(3, TimeUnit.SECONDS)) {
                    Database.connection.logEvent(
                        EventLog("minestom_error", Severity.ERROR)
                            .withProperty("exception", throwable::class.qualifiedName)
                            .withProperty("message", throwable.message)
                            .withProperty("stack_trace", throwable.stackTraceToString())
                    )
                }
            }
        }
    }

}
