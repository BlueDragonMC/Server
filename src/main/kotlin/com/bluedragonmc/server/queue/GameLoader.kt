package com.bluedragonmc.server.queue

import com.bluedragonmc.server.Game
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
            // Preload the game's main class
            val ms = measureTimeMillis {
                val classLoader = GameClassLoader(arrayOf(path.toUri().toURL()))
                GameClassLoader.loaders.add(classLoader)
                val clazz = classLoader.loadClass(mainClass)
                classes[name] = clazz as Class<out Game>
            }
            logger.info("Initialized plugin '${props.getProperty("name")}' (${path.name}) in ${ms}ms")
        }
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

    fun createNewGame(name: String, mapName: String?, mode: String?): Game {
        val ctor = classes[name]?.kotlin?.primaryConstructor ?: error("Game class not found or improperly loaded")
        if (ctor.parameters.isNotEmpty() && mapName.isNullOrBlank()) error("Map name is required by game, but not supplied")
        return when(ctor.parameters.size) {
            0 -> ctor.call()
            1 -> ctor.call(mapName)
            2 -> ctor.call(mapName, mode)
            else -> error("Unexpected constructor format: $ctor")
        }
    }
}