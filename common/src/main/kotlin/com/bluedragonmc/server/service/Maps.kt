package com.bluedragonmc.server.service

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.module.config.ConfigModule
import net.minestom.server.instance.ChunkLoader
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.io.BufferedReader
import java.io.StringReader
import java.util.*

object Maps {
    data class MapSource(
        /**
         * The unique identifier for this map.
         */
        val id: String,
        /**
         * URL for the binary data representing the map.
         */
        val url: String,
        /**
         * The format used to encode this map's data.
         */
        val format: CommonTypes.MapFormat,
        /**
         * The map's root configuration node. By convention, map-specific entries are under the "world" node.
         */
        val config: ConfigurationNode,
    ) {
        constructor(id: String, url: String, format: CommonTypes.MapFormat, config: String) : this(id, url, format,
            ConfigModule.loadFile(BufferedReader(StringReader(config)))
        )
        val games: List<GameEntry> by lazy { config.node("world", "games").getList(GameEntry::class.java)!! }
        val whitelist: List<UUID>? by lazy {
            if (!config.node("world").hasChild("whitelist")) return@lazy null
            config.node("world", "whitelist").getList(UUID::class.java)
        }

        /**
         * Returns true if this map is playable on the specified game, false otherwise.
         */
        infix fun matches(gameType: CommonTypes.GameType): Boolean =
            (!gameType.hasMapId() || gameType.mapId == id)
                && games.any { game ->
                    game.name == gameType.name
                        && (game.mode == null || game.mode == gameType.mode)
                }

        /**
         * Returns true if the player is not blocked from joining this map by the whitelist.
         */
        fun isPlayerAllowed(playerUuid: UUID) = whitelist?.contains(playerUuid) != false
    }

    @ConfigSerializable
    data class GameEntry(
        val name: String,
        val mode: String?,
    ) {
        // for configurate
        constructor() : this("", "")
    }

    abstract class MapProvider {
        abstract suspend fun provideMap(source: MapSource): ChunkLoader
    }

    private val mapProviders = mutableMapOf<CommonTypes.MapFormat, MapProvider>()

    suspend fun provideMap(source: MapSource): ChunkLoader =
        mapProviders[source.format]?.provideMap(source) ?: error("No valid map provider found to fulfill request: $source")

    fun registerMapProvider(format: CommonTypes.MapFormat, mapProvider: MapProvider) {
        mapProviders[format] = mapProvider
    }
}