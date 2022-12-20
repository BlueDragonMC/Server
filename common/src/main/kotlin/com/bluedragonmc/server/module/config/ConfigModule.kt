package com.bluedragonmc.server.module.config

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.config.serializer.*
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
import java.nio.file.Paths
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists

class ConfigModule(private val configFileName: String? = null) : GameModule() {

    private lateinit var root: ConfigurationNode
    private lateinit var parent: Game

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

    private fun loadFile(path: String): ConfigurationNode {

        val overrideFile = Paths.get(externalFolder, path)
        val reader = if (overrideFile.exists()) {
            overrideFile.bufferedReader()
        } else {
            parent::class.java.classLoader.getResourceAsStream(internalFolder + path)!!.bufferedReader()
        }

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

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        if (configFileName != null) {
            root = loadFile(configFileName)
        }
    }

    fun getConfig(): ConfigurationNode {
        if (::root.isInitialized) {
            return root
        } else {
            throw IllegalStateException("ConfigModule created with no default config file path!")
        }
    }

    fun loadExtra(fileName: String): ConfigurationNode? {
        return runCatching {
            loadFile(fileName)
        }.getOrNull()
    }
}