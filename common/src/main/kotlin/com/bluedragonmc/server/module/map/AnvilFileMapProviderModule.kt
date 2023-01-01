package com.bluedragonmc.server.module.map

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.InstanceUtils
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.SharedInstance
import net.minestom.server.tag.Tag
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class AnvilFileMapProviderModule(val worldFolder: Path) : GameModule() {

    lateinit var instanceContainer: InstanceContainer
        private set

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        if (loadedMaps.containsKey(worldFolder)) {
            instanceContainer = loadedMaps[worldFolder]!!
            return
        }
        instanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer().apply {
            chunkLoader = AnvilLoader(worldFolder)
            loadedMaps[worldFolder] = this
            setTag(MAP_NAME_TAG, worldFolder.absolutePathString())
        }
    }

    companion object {

        private val logger = LoggerFactory.getLogger(Companion::class.java)

        val loadedMaps = mutableMapOf<Path, InstanceContainer>()

        val MAP_NAME_TAG = Tag.String("anvil_file_map_name")

        /**
         * This method should be called when a SharedInstance is unregistered.
         * It will check if the unregistrered instance is the last instance
         * which depends on an Anvil map to be loaded, and if so, unloads the map.
         */
        fun checkReleaseMap(instance: Instance) {
            if (instance !is SharedInstance) return
            val isLast = MinecraftServer.getInstanceManager().instances.none {
                it !== instance && it is SharedInstance && it.instanceContainer === instance.instanceContainer
            }
            if (isLast) {
                val key = loadedMaps.entries.find { it.value === instance.instanceContainer }?.key
                loadedMaps.remove(key)
                InstanceUtils.forceUnregisterInstance(instance.instanceContainer)
                logger.info("Map file '${key?.fileName}' has been unloaded and its instance container has been unregistered.")
            }
        }
    }
}
