package com.bluedragonmc.server.utils

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.ChunkLoader
import net.minestom.server.instance.Instance

/**
 * A [ChunkLoader] that waits for another [ChunkLoader] to become available before delegating to it.
 *
 * This makes it possible to create an [Instance] immediately while a map/world is still being
 * downloaded asynchronously. All chunk operations block only the calling (chunk-load) thread
 * until the delegate loader is ready.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeferredChunkLoader(private val delegate: Deferred<ChunkLoader>) : ChunkLoader {

    override fun loadChunk(instance: Instance?, chunkX: Int, chunkZ: Int): Chunk? =
        runBlocking { delegate.await().loadChunk(instance, chunkX, chunkZ) }

    override fun saveChunk(chunk: Chunk) =
        runBlocking { delegate.await().saveChunk(chunk) }

    override fun saveChunks(chunks: Collection<Chunk>) =
        runBlocking { delegate.await().saveChunks(chunks) }

    // `InstanceContainer`'s constructor calls loadInstance immediately, which may be on the tick
    // thread, so this must not block. The real loader's loadInstance is invoked once it is
    // assigned to the instance.
    override fun loadInstance(instance: Instance) {
        if (delegate.isCompleted) {
            runCatching { delegate.getCompleted().loadInstance(instance) }
        }
    }

    override fun saveInstance(instance: Instance) =
        runBlocking { delegate.await().saveInstance(instance) }

    override fun unloadChunk(chunk: Chunk) =
        runBlocking { delegate.await().unloadChunk(chunk) }

    // These are queried on the thread requesting a chunk load, which may be the tick thread, so
    // they must never block.
    override fun supportsParallelSaving(): Boolean =
        delegate.isCompleted && runCatching { delegate.getCompleted().supportsParallelSaving() }.getOrDefault(false)

    // While the delegate is still loading, assume parallel loading is
    // supported so Minestom performs the (blocking) load on a virtual thread.
    override fun supportsParallelLoading(): Boolean =
        !delegate.isCompleted || runCatching { delegate.getCompleted().supportsParallelLoading() }.getOrDefault(true)
}
