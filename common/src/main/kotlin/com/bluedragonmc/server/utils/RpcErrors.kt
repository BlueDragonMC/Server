package com.bluedragonmc.server.utils

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import org.slf4j.Logger

/** Classification and logging for gRPC messaging failures */
object RpcErrors {

    /**
     * Status codes that indicate an issue with the underlying connection, not the application.
     */
    private val TRANSPORT_CODES = setOf(
        Status.Code.UNAVAILABLE,
        Status.Code.DEADLINE_EXCEEDED,
        Status.Code.CANCELLED,
        Status.Code.ABORTED,
        Status.Code.RESOURCE_EXHAUSTED,
    )

    private fun statusOf(throwable: Throwable): Status? {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            when (current) {
                is StatusException -> return current.status
                is StatusRuntimeException -> return current.status
            }
            current = current.cause
            depth++
        }
        return null
    }

    /**
     * Logs [throwable]. Prints out the full stack trace for application failures
     * and just a single line for connection/transport errors.
     */
    fun log(message: String, throwable: Throwable, logger: Logger) {
        val status = statusOf(throwable)
        when {
            status == null -> logger.error(message, throwable)
            status.code in TRANSPORT_CODES -> {
                val detail = status.description ?: status.cause?.message ?: "no details"
                logger.warn("{}: {} ({})", message, status.code, detail)
            }
            else -> logger.error("{}: {}", message, status.code, throwable)
        }
    }

    /**
     * Builds a [CoroutineExceptionHandler] that uses [log] for uncaught exceptions in a CoroutineScope.
     */
    fun handler(label: String, logger: Logger): CoroutineExceptionHandler =
        CoroutineExceptionHandler { context, throwable ->
            val name = context[CoroutineName]?.name ?: label
            log("Uncaught exception in '$name' coroutine", throwable, logger)
        }

    private const val MAX_CAUSE_DEPTH = 8
}
