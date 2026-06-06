package com.bluedragonmc.server.impl

import com.bluedragonmc.server.service.Maps
import kotlinx.coroutines.suspendCancellableCoroutine
import net.hollowcube.polar.PolarDataConverter
import net.hollowcube.polar.PolarLoader
import net.hollowcube.polar.PolarWorld
import net.hollowcube.polar.PolarWriter
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.anvil.AnvilLoader
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import java.net.URI
import java.nio.file.Paths
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val client = OkHttpClient()

class PolarMapProvider : Maps.MapProvider<PolarLoader>() {
    override suspend fun provideMap(source: Maps.MapSource): PolarLoader {
        println("Providing Polar map at ${source.url}")
        val request = Request.Builder().url(source.url).build()
        val response = client.newCall(request).await()
        val body = response.body!!
        println("Got response of length ${body.contentLength()}")
        if (body.contentLength() == 0L) {
            println("Map has no contents. Providing an empty map with default values.")
            return PolarLoader(PolarWorld())
        }
        return PolarLoader(response.body!!.byteStream())
    }

    override suspend fun saveMap(source: Maps.MapSource, instance: InstanceContainer) {
        val loader = instance.chunkLoader as PolarLoader
        instance.saveInstance() // update Polar's internal representation of the world (will not write any data to disk)
        val mapBytes = PolarWriter.write(loader.world(), PolarDataConverter.NOOP)
        val request = Request.Builder().url(source.url).method("POST", mapBytes.toRequestBody()).build()
        client.newCall(request).await()
    }
}

class AnvilMapProvider : Maps.MapProvider<AnvilLoader>() {
    override suspend fun provideMap(source: Maps.MapSource): AnvilLoader {
        println("Providing Anvil map at ${source.url}")
        return AnvilLoader(Paths.get(URI.create(source.url)))
    }

    override suspend fun saveMap(source: Maps.MapSource, instance: InstanceContainer) {
        TODO()
    }
}

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) {
                    cont.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })

        cont.invokeOnCancellation {
            cancel()
        }
    }