package com.bluedragonmc.server.module.config

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.config.serializer.*
import com.bluedragonmc.server.module.minigame.KitsModule
import com.bluedragonmc.server.service.Maps
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.minestom.server.color.Color
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.EnchantmentList
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.BufferedReader
import java.nio.file.Paths
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists

class ConfigModule(private val configFileName: String? = null, private val mapSource: Maps.MapSource? = null) : GameModule() {

    private lateinit var root: ConfigurationNode
    private lateinit var mapRoot: ConfigurationNode

    private lateinit var parent: Game

    private var initialized = false

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        val mapSource = mapSource ?: parent.data.mapSource
        if (configFileName != null) {
            logger.info("Loading game configuration from $configFileName")
            root = loadFile(getReader(parent, configFileName))
        }

        runBlocking {
            mapRoot = mapSource.config
        }

        logger.info("Configuration successfully loaded.")
        initialized = true
    }

    fun getConfig(): ConfigurationNode {

        if (!initialized) {
            throw IllegalStateException("Accessing ConfigModule before it was initialized.")
        }

        if (!::root.isInitialized) {
            if (::mapRoot.isInitialized) {
                return mapRoot
            } else {
                throw IllegalStateException("No game or map configuration found!")
            }
        }

        if (::mapRoot.isInitialized) {
            return root.mergeFrom(mapRoot)
        } else {
            return root
        }
    }

    companion object {

        /**
         * Files in this folder will be treated as overrides
         * to config placed inside the compiled JAR.
         */
        private val externalFolder = "/etc/config/"

        /**
         *  JAR (zip) entries in this folder inside the compiled JAR will act as
         *  fallbacks for any configuration that isn't in the external folder.
         */
        private val internalFolder = "config/"

        private fun getReader(game: Game, path: String): BufferedReader {
            val overrideFile = Paths.get(externalFolder, path)
            return if (overrideFile.exists()) {
                overrideFile.bufferedReader()
            } else {
                game::class.java.classLoader.getResourceAsStream(internalFolder + path)!!.bufferedReader()
            }
        }

        val SERIALIZATION_OPTIONS: ConfigurationOptions = ConfigurationOptions.defaults().serializers { builder ->
            builder.register(Point::class.java, PointSerializer())
            builder.register(Color::class.java, ColorSerializer())
            builder.register(Component::class.java, ComponentSerializer())
            builder.register(EntityType::class.java, EntityTypeSerializer())
            builder.register(Material::class.java, MaterialSerializer())
            builder.register(EnchantmentList::class.java, EnchantmentListSerializer())
            builder.register(PlayerSkin::class.java, PlayerSkinSerializer())
            builder.register(KitsModule.Kit::class.java, KitSerializer())
            builder.register(ItemStack::class.java, ItemStackSerializer())
            builder.register(Block::class.java, BlockSerializer())
        }

        fun loadFile(reader: BufferedReader): ConfigurationNode {
            val loader = YamlConfigurationLoader.builder()
                .source { reader }
                .build()

            return loader.load(SERIALIZATION_OPTIONS)
        }

        fun loadExtra(game: Game, fileName: String): ConfigurationNode? {
            return runCatching {
                loadFile(getReader(game, fileName))
            }.getOrNull()
        }
    }
}