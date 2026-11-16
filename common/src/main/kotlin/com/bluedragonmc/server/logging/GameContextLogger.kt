package com.bluedragonmc.server.logging

import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger

/**
 * A logger that adds context to every log event.
 */
internal class GameContextLogger(
    private val delegate: Logger,
    private val context: () -> Map<String, String>,
) : LegacyAbstractLogger() {

    init {
        name = delegate.name
    }

    override fun getName(): String = delegate.name

    override fun getFullyQualifiedCallerName(): String = GameContextLogger::class.java.name

    override fun isTraceEnabled(): Boolean = delegate.isTraceEnabled

    override fun isDebugEnabled(): Boolean = delegate.isDebugEnabled

    override fun isInfoEnabled(): Boolean = delegate.isInfoEnabled

    override fun isWarnEnabled(): Boolean = delegate.isWarnEnabled

    override fun isErrorEnabled(): Boolean = delegate.isErrorEnabled

    override fun isTraceEnabled(marker: Marker?): Boolean = delegate.isTraceEnabled(marker)

    override fun isDebugEnabled(marker: Marker?): Boolean = delegate.isDebugEnabled(marker)

    override fun isInfoEnabled(marker: Marker?): Boolean = delegate.isInfoEnabled(marker)

    override fun isWarnEnabled(marker: Marker?): Boolean = delegate.isWarnEnabled(marker)

    override fun isErrorEnabled(marker: Marker?): Boolean = delegate.isErrorEnabled(marker)

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any?>?,
        throwable: Throwable?,
    ) {
        val fields = context()
        if (fields.isEmpty()) {
            delegateLog(level, marker, messagePattern, arguments, throwable)
        } else {
            LogContext.with(fields) {
                delegateLog(level, marker, messagePattern, arguments, throwable)
            }
        }
    }

    private fun delegateLog(
        level: Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any?>?,
        throwable: Throwable?,
    ) {
        val builder = delegate.atLevel(level)
        if (marker != null) builder.addMarker(marker)
        if (throwable != null) builder.setCause(throwable)
        arguments?.forEach { builder.addArgument(it) }
        if (messagePattern == null) builder.log() else builder.log(messagePattern)
    }
}
