package com.bluedragonmc.server.queue

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.bootstrap.GlobalTranslation
import com.bluedragonmc.server.game.GameData
import com.bluedragonmc.server.game.GameModeProvider
import com.bluedragonmc.server.game.GameModes
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import kotlin.io.path.*
import kotlin.reflect.full.primaryConstructor
import kotlin.system.measureTimeMillis

object GameLoader {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val gamesList by lazy {
        Paths.get("games").listDirectoryEntries().filter { it.isRegularFile() }.associateWith(::getPluginProperties)
    }

    val gameNames by lazy {
        gamesList.map { it.value.getProperty("name") }
    }

    private val classes = mutableMapOf<String, Class<out Game>>()

    fun loadGames() {
        gamesList.forEach { (path, props) ->
            val name = props.getProperty("name")
            val mainClass = props.getProperty("main-class")
            // Load the game's main class, translation keys, and mode provider
            val ms = measureTimeMillis {
                val classLoader = GameClassLoader(arrayOf(path.toUri().toURL()))
                GlobalTranslation.addSource(name.lowercase().replace(Regex("[^a-zA-Z0-9]"), "_"), classLoader)
                val clazz = classLoader.loadClass(mainClass)
                classes[name] = clazz as Class<out Game>

                val modeProviderName = props.getProperty("mode-provider")
                if (modeProviderName != null) {
                    GameModes.register(name, loadModeProvider(classLoader, modeProviderName, name))
                }
            }
            logger.info("Initialized plugin '$name' (${path.name}) in ${ms}ms")
        }
    }

    private fun loadModeProvider(
        classLoader: ClassLoader,
        className: String,
        gameName: String,
    ): GameModeProvider {
        val clazz = classLoader.loadClass(className)
        val instance = runCatching {
            // Kotlin objects expose a static INSTANCE field.
            clazz.getField("INSTANCE").get(null)
        }.getOrElse {
            clazz.getDeclaredConstructor().newInstance()
        }
        return instance as? GameModeProvider
            ?: error("Mode provider '$className' for game '$gameName' does not implement GameModeProvider")
    }

    private fun getPluginProperties(jarPath: Path): Properties {
        val inputStream = JarInputStream(BufferedInputStream(jarPath.inputStream()))
        var entry: JarEntry
        while(true) {
            entry = inputStream.nextJarEntry ?: break
            if (entry.name == "game.properties") {
                val props = Properties().apply {
                    load(inputStream)
                }
                return props
            }
        }
        error("No 'game.properties' file found in path: ${jarPath.pathString}")
    }

    fun createNewGame(data: GameData): Game {
        val gameName = data.name
        GameModes.require(gameName, data.mode)
        val ctor = classes[gameName]?.kotlin?.primaryConstructor
            ?: error("Game class not found or improperly loaded for '$gameName'")
        val game = ctor.call(data)
        game.init()
        return game
    }
}