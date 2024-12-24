package com.bluedragonmc.server.module.map

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceUnregisterEvent
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.registry.DynamicRegistry
import net.minestom.server.tag.Tag
import net.minestom.server.world.DimensionType
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
        val loadedMaps = mutableMapOf<Path, InstanceContainer>()
        val MAP_NAME_TAG = Tag.String("anvil_file_map_name")

        init {
            // If an InstanceContainer is unregistered, remove it from `loadedMaps` so it can be garbage collected
            MinecraftServer.getGlobalEventHandler().addListener(InstanceUnregisterEvent::class.java) { event ->
                loadedMaps.entries.removeIf { (_, instance) -> instance == event.instance }
            }
        }
    }
}
