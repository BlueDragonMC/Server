package com.bluedragonmc.server.module.map

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Maps
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceUnregisterEvent
import net.minestom.server.instance.*
import net.minestom.server.registry.RegistryKey
import net.minestom.server.tag.Tag
import net.minestom.server.world.DimensionType

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
class MapProviderModule(
    val mapSource: Maps.MapSource,
    private val dimensionType: RegistryKey<DimensionType> = DimensionType.OVERWORLD
) : GameModule() {
    lateinit var instanceContainer: InstanceContainer
        private set

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        // If this world has already been loaded, use its existing InstanceContainer
        if (loadedMaps.containsKey(mapSource.id)) {
            instanceContainer = loadedMaps[mapSource.id]!!
            return
        }

        // If not, create a new InstanceContainer
        instanceContainer = MinecraftServer.getInstanceManager().createInstanceContainer(dimensionType)
        instanceContainer.chunkLoader = DeferredChunkLoader(Database.IO.async {
            Maps.provideMap(mapSource)
        })
        instanceContainer.setChunkSupplier(::LightingChunk)
        instanceContainer.setTag(MAP_NAME_TAG, mapSource.id)

        loadedMaps[mapSource.id] = instanceContainer
    }

    companion object {
        val loadedMaps = mutableMapOf<String, InstanceContainer>()
        val MAP_NAME_TAG = Tag.String("anvil_file_map_name")

        init {
            // If an InstanceContainer is unregistered, remove it from `loadedMaps` so it can be garbage collected
            MinecraftServer.getGlobalEventHandler().addListener(InstanceUnregisterEvent::class.java) { event ->
                loadedMaps.entries.removeIf { (_, instance) -> instance == event.instance }
            }
        }
    }

    private class DeferredChunkLoader(val delegate: Deferred<ChunkLoader>) : ChunkLoader {
        override fun loadChunk(
            p0: Instance?, p1: Int, p2: Int
        ): Chunk? = runBlocking { delegate.await().loadChunk(p0, p1, p2) }

        override fun saveChunk(p0: Chunk?) = runBlocking { delegate.await().saveChunk(p0) }
    }
}
