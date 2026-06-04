package com.bluedragonmc.server.impl

import com.bluedragonmc.server.service.Maps
import kotlinx.coroutines.suspendCancellableCoroutine
import net.hollowcube.polar.PolarLoader
import net.minestom.server.instance.ChunkLoader
import net.minestom.server.instance.anvil.AnvilLoader
import okhttp3.*
import okio.IOException
import java.net.URI
import java.nio.file.Paths
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val client = OkHttpClient()

class PolarMapProvider : Maps.MapProvider() {
    override suspend fun provideMap(source: Maps.MapSource): ChunkLoader {
        println("Providing Polar map at ${source.url}")
        val request = Request.Builder().url(source.url).build()
        val response = client.newCall(request).await()
        return PolarLoader(response.body!!.byteStream())
    }
}

class AnvilMapProvider : Maps.MapProvider() {
    override suspend fun provideMap(source: Maps.MapSource): ChunkLoader {
        println("Providing Anvil map at ${source.url}")
        println("Path: ${Paths.get(URI.create(source.url))}")
        return AnvilLoader(Paths.get(URI.create(source.url)))
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