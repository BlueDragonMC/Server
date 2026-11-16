package com.bluedragonmc.server.game

import net.kyori.adventure.text.Component

/**
 * A stable, canonical identifier for a game mode.
 */
@JvmInline
value class GameModeId private constructor(val value: String) {

    override fun toString() = value

    companion object {
        private val VALID = Regex("[a-z0-9]+(?:[_-][a-z0-9]+)*")

        /** The implicit mode used by games that do not declare any modes of their own. */
        val DEFAULT = of("default")

        fun of(raw: String): GameModeId {
            require(raw.isNotEmpty()) { "Game mode id cannot be empty" }
            require(raw == raw.trim()) { "Game mode id '$raw' must not have surrounding whitespace" }
            require(raw == raw.lowercase()) { "Game mode id '$raw' must be lowercase" }
            require(VALID.matches(raw)) {
                "Game mode id '$raw' must be a lowercase slug (letters, digits, '_' or '-' between words)"
            }
            return GameModeId(raw)
        }

        /**
         * Forgivingly normalizes user input into a game mode ID.
         */
        fun fromInput(raw: String): GameModeId {
            val normalized = raw.trim()
                .lowercase()
                .replace(Regex("[^a-z0-9_-]+"), "-")
                .trim('-')
            require(normalized.isNotEmpty()) { "Game mode id cannot be blank" }
            return GameModeId(normalized)
        }

        fun fromInputOrNull(raw: String?): GameModeId? =
            raw?.takeIf { it.isNotBlank() }?.let { runCatching { fromInput(it) }.getOrNull() }
    }
}

interface GameMode {
    val id: GameModeId

    /** Optional human-readable name */
    val displayName: Component? get() = null

    /** Optional longer description */
    val description: Component? get() = null

    /** Hide this mode from `/join` command suggestions and lobby menus */
    val hidden: Boolean get() = false
}

/**
 * Implemented by a game to declare its game modes.
 */
interface GameModeProvider {
    val modes: Collection<GameMode>

    /**
     * The mode used by `/join <game>` (without a mode specified) and quick join.
     * Defaults to the first declared mode. Must be contained in [modes].
     */
    val defaultMode: GameMode? get() = modes.firstOrNull()
}
