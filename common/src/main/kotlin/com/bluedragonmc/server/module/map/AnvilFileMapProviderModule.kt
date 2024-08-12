package com.bluedragonmc.server.module.map

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.InstanceUtils
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.*
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.registry.DynamicRegistry
import net.minestom.server.tag.Tag
import net.minestom.server.world.DimensionType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Supplies an [InstanceContainer] to the [com.bluedragonmc.server.module.instance.InstanceContainerModule].
 *
 * **Note**: By default, [lighting](https://wiki.minestom.net/world/chunk-management/lightloader) is enabled by setting the chunk supplier to the [LightingChunk] constructor.
 * To disable this, you can revert it back to [DynamicChunk] like so:
 * ```kotlin
 * use(AnvilFileMapProviderModule(path)) { module ->
 *   module.getInstance().setChunkSupplier(::DynamicChunk)
 * }
 * ```
 * 
 * [See Documentation](https://developer.bluedragonmc.com/modules/anvilfilemapprovidermodule/)
 */
class AnvilFileMapProviderModule(val worldFolder: Path, private val dimensionType: DynamicRegistry.Key<DimensionType> = DimensionType.OVERWORLD) : GameModule() {

    lateinit var instanceContainer: InstanceContainer
        private set

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        // If this world has already been loaded, use its existing InstanceContainer
        if (loadedMaps.containsKey(worldFolder)) {
            instanceContainer = loadedMaps[worldFolder]!!
            return
        }

        // If not, create a new InstanceContainer
        instanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer(dimensionType)
        instanceContainer.chunkLoader = AnvilLoader(worldFolder)
        instanceContainer.setChunkSupplier(::LightingChunk)
        instanceContainer.setTag(MAP_NAME_TAG, worldFolder.absolutePathString())

        loadedMaps[worldFolder] = instanceContainer
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
