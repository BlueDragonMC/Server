package com.bluedragonmc.server.module.config

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.SoftDependsOn
import com.bluedragonmc.server.module.config.serializer.*
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.KitsModule
import net.kyori.adventure.text.Component
import net.minestom.server.color.Color
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Enchantment
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.BufferedReader
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.io.path.reader

@SoftDependsOn(AnvilFileMapProviderModule::class)
class ConfigModule(private val configFileName: String? = null) : GameModule() {

    private lateinit var root: ConfigurationNode
    private lateinit var mapRoot: ConfigurationNode

    private lateinit var parent: Game

    private var initialized = false

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        if (configFileName != null) {
            logger.info("Loading game configuration from $configFileName")
            root = loadFile(getReader(parent, configFileName))
        }

        if (parent.hasModule<AnvilFileMapProviderModule>()) {
            val worldFolder = parent.getModule<AnvilFileMapProviderModule>().worldFolder
            val file = worldFolder.resolve("config.yml")
            if (file.exists()) {
                logger.info("Loading map configuration from " + file.absolutePathString())
                mapRoot = loadFile(file.reader(Charsets.UTF_8).buffered())
            } else {
                logger.info("No map configuration found at " + file.absolutePathString())
            }
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

        private fun loadFile(reader: BufferedReader): ConfigurationNode {

            val loader = YamlConfigurationLoader.builder()
                .source { reader }
                .build()

            val config = ConfigurationOptions.defaults().serializers { builder ->
                builder.register(Pos::class.java, PosSerializer())
                builder.register(Color::class.java, ColorSerializer())
                builder.register(Component::class.java, ComponentSerializer())
                builder.register(EntityType::class.java, EntityTypeSerializer())
                builder.register(Material::class.java, MaterialSerializer())
                builder.register(Enchantment::class.java, EnchantmentSerializer())
                builder.register(PlayerSkin::class.java, PlayerSkinSerializer())
                builder.register(KitsModule.Kit::class.java, KitSerializer())
                builder.register(ItemStack::class.java, ItemStackSerializer())
                builder.register(Block::class.java, BlockSerializer())
            }
            return loader.load(config)
        }

        fun loadExtra(game: Game, fileName: String): ConfigurationNode? {
            return runCatching {
                loadFile(getReader(game, fileName))
            }.getOrNull()
        }
    }
}