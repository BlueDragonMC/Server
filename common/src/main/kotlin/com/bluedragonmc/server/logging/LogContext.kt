package com.bluedragonmc.server.logging

import com.bluedragonmc.server.Game
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import org.slf4j.MDC
import java.util.*
import kotlin.coroutines.CoroutineContext

/** Structured logging helpers using SLF4J MDC */
object LogContext {

    fun fields(vararg pairs: Pair<String, Any?>): Map<String, String> = buildMap {
        for ((key, value) in pairs) {
            val text = value?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) put(key, text)
        }
    }

    fun player(player: Player): Map<String, String> = fields(
        "player" to player.username,
        "player_uuid" to player.uuid,
    )

    fun player(uuid: UUID, username: String? = null): Map<String, String> = fields(
        "player" to username,
        "player_uuid" to uuid,
    )

    fun game(game: Game, instance: Instance? = null): Map<String, String> = game(
        gameId = game.id,
        game = game.data.name,
        map = game.data.mapSource.id,
        instance = instance,
    )

    fun game(
        gameId: String?,
        game: String? = null,
        map: String? = null,
        instance: Instance? = null,
    ): Map<String, String> = fields(
        "game" to game,
        "game_id" to gameId,
        "map" to map,
        "instance_uuid" to instance?.uuid,
    )

    fun server(): Map<String, String> = fields(
        "server" to System.getenv("BLUEDRAGON_SERVER_NAME"),
    )

    fun <T> with(vararg pairs: Pair<String, Any?>, block: () -> T): T =
        with(fields(*pairs), block)

    fun <T> with(fields: Map<String, String>, block: () -> T): T {
        if (fields.isEmpty()) return block()

        val previous = fields.keys.associateWith { MDC.get(it) }
        try {
            fields.forEach { (key, value) -> MDC.put(key, value) }
            return block()
        } finally {
            previous.forEach { (key, value) ->
                if (value == null) MDC.remove(key) else MDC.put(key, value)
            }
        }
    }

    suspend fun <T> withSuspend(
        fields: Map<String, String>,
        block: suspend () -> T,
    ): T = withContext(LogContextElement(fields)) { block() }

    suspend fun <T> withSuspend(
        vararg pairs: Pair<String, Any?>,
        block: suspend () -> T,
    ): T = withSuspend(fields(*pairs)) { block() }

    private class LogContextElement(
        private val fields: Map<String, String>,
    ) : ThreadContextElement<Map<String, String?>> {

        companion object Key : CoroutineContext.Key<LogContextElement>

        override val key: CoroutineContext.Key<LogContextElement> = Key

        override fun updateThreadContext(context: CoroutineContext): Map<String, String?> {
            val previous = fields.keys.associateWith { MDC.get(it) }
            fields.forEach { (key, value) -> MDC.put(key, value) }
            return previous
        }

        override fun restoreThreadContext(
            context: CoroutineContext,
            oldState: Map<String, String?>,
        ) {
            oldState.forEach { (key, value) ->
                if (value == null) MDC.remove(key) else MDC.put(key, value)
            }
        }
    }
}
