package com.bluedragonmc.server.service

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.module.config.ConfigModule
import net.minestom.server.instance.ChunkLoader
import net.minestom.server.instance.InstanceContainer
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.gson.GsonConfigurationLoader
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
         * Raw configuration for this map, parsed on each access.
         */
        private val rawConfig: String,
    ) {
        /**
         * Parses this map's configuration using the provided serialization [options].
         */
        fun parse(options: ConfigurationOptions): ConfigurationNode =
            ConfigModule.loadFile(BufferedReader(StringReader(rawConfig)), options)

        /**
         * The map's root configuration node. By convention, map-specific entries are under the "world" node.
         */
        val config: ConfigurationNode
            get() = parse(ConfigModule.SERIALIZATION_OPTIONS)

        val games: List<GameEntry> by lazy { config.node("world", "games").getList(GameEntry::class.java)!! }
        val whitelist: List<UUID>? by lazy {
            val config = config
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

    abstract class MapProvider<L : ChunkLoader> {
        abstract suspend fun provideMap(source: MapSource): L

        /**
         * Serializes the current contents of [instance] into the map's binary format.
         */
        abstract fun serializeMap(instance: InstanceContainer): ByteArray

        /**
         * Posts serialized map [bytes] to the map's URL.
         */
        abstract suspend fun uploadMap(source: MapSource, bytes: ByteArray)

        /**
         * Serializes and uploads the map. Prefer running [serializeMap] on the tick thread
         * and then [uploadMap] on a background thread instead of using this method.
         */
        suspend fun saveMap(source: MapSource, instance: InstanceContainer) =
            uploadMap(source, serializeMap(instance))
    }

    private val mapProviders = mutableMapOf<CommonTypes.MapFormat, MapProvider<*>>()

    suspend fun provideMap(source: MapSource): ChunkLoader =
        mapProviders[source.format]?.provideMap(source)
            ?: error("No valid map provider found to fulfill request: $source")

    fun serializeMap(source: MapSource, instance: InstanceContainer): ByteArray =
        (mapProviders[source.format] as? MapProvider<ChunkLoader>)?.serializeMap(instance)
            ?: error("No valid map provider found to fulfill serialize request: $source")

    suspend fun uploadMap(source: MapSource, bytes: ByteArray) =
        (mapProviders[source.format] as? MapProvider<ChunkLoader>)?.uploadMap(source, bytes)
            ?: error("No valid map provider found to fulfill save request: $source")

    suspend fun saveMap(source: MapSource, instance: InstanceContainer) =
        (mapProviders[source.format] as? MapProvider<ChunkLoader>)?.saveMap(source, instance)
            ?: error("No valid map provider found to fulfill save request: $source")

    suspend fun saveMapConfig(source: MapSource, config: ConfigurationNode) {
        Messaging.outgoing.updateMapConfig(source.id, serializeMapConfig(config))
    }

    /**
     * Serializes a map configuration to JSON.
     */
    fun serializeMapConfig(config: ConfigurationNode): String =
        GsonConfigurationLoader.builder().buildAndSaveString(config)

    fun registerMapProvider(format: CommonTypes.MapFormat, mapProvider: MapProvider<*>) {
        mapProviders[format] = mapProvider
    }
}
