package com.bluedragonmc.server.game

import org.slf4j.LoggerFactory

/**
 * Runtime registry of every game mode declared by the loaded game plugins.
 */
object GameModes {

    private val logger = LoggerFactory.getLogger(GameModes::class.java)

    private val providers = LinkedHashMap<String, GameModeProvider>()

    private val implicitDefault = object : GameMode {
        override val id = GameModeId.DEFAULT
        override val hidden = true
    }

    /** Registers the game modes declared by a game plugin. */
    fun register(gameName: String, provider: GameModeProvider) {
        require(gameName.isNotEmpty()) { "Game name cannot be blank" }
        require(!providers.containsKey(gameName)) { "Game '$gameName' already has registered modes" }

        val modes = provider.modes
        if (modes.isEmpty()) {
            logger.warn("Game '$gameName' declared an empty mode provider; falling back to the implicit default mode")
            return
        }

        val ids = modes.map { it.id }
        require(ids.distinct().size == ids.size) { "Game '$gameName' declares duplicate mode ids" }

        modes.forEach { mode ->
            require(mode.id != GameModeId.DEFAULT) {
                "Game '$gameName' cannot declare a mode with the reserved id 'default'; omit the provider instead"
            }
        }

        val default = defaultOf(provider)
        require(default.id in ids) {
            "Game '$gameName' default mode '${default.id}' is not in its mode list"
        }

        providers[gameName] = provider
        logger.info("Registered ${modes.size} mode(s) for '$gameName': ${modes.joinToString { it.id.value }}")
    }

    /** True when [game] declares explicit modes. Games without modes use the implicit default. */
    fun hasModes(game: String): Boolean = providers[game]?.modes?.isNotEmpty() == true

    /** All registered modes for [game], including hidden ones. */
    fun modes(game: String): Collection<GameMode> = providers[game]?.modes ?: emptyList()

    /** Registered modes that are safe to show in `/join` suggestions and menus. */
    fun visibleModes(game: String): List<GameMode> = modes(game).filterNot { it.hidden }

    fun defaultFor(game: String): GameMode =
        providers[game]?.let(::defaultOf) ?: implicitDefault

    /**
     * Resolves [input] as a mode id. A blank input resolves to the default mode.
     * Returns null when the game declares modes and the requested id is not one of them.
     */
    fun resolve(game: String, input: String?): GameMode? {
        val provider = providers[game]
        if (input.isNullOrBlank()) return provider?.let(::defaultOf) ?: implicitDefault

        val id = GameModeId.fromInputOrNull(input) ?: return null
        if (provider == null) return implicitDefault.takeIf { id == GameModeId.DEFAULT }
        return provider.modes.firstOrNull { it.id == id }
    }

    /** Resolves a canonical [mode] id, throwing an error if it is not registered. */
    fun require(game: String, mode: GameModeId): GameMode {
        val provider = providers[game]
        if (provider == null) {
            require(mode == GameModeId.DEFAULT) { "Unknown mode '$mode' for game '$game'" }
            return implicitDefault
        }
        return provider.modes.firstOrNull { it.id == mode }
            ?: error("Unknown mode '$mode' for game '$game'")
    }

    fun validModeIds(game: String): List<String> = visibleModes(game).map { it.id.value }

    private fun defaultOf(provider: GameModeProvider): GameMode =
        provider.defaultMode ?: provider.modes.first()
}
