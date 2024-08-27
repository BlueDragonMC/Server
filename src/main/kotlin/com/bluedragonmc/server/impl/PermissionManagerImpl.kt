package com.bluedragonmc.server.impl

import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.PermissionManager
import com.bluedragonmc.server.api.PlayerMeta
import com.bluedragonmc.server.utils.miniMessage
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class PermissionManagerImpl : PermissionManager {

    private val baseUrl = Environment.current.luckPermsHostname
    private val cacheControl = CacheControl.Builder().maxAge(10, TimeUnit.SECONDS).build()

    private val client = OkHttpClient.Builder()
        .cache(Cache(File("/tmp/okhttp-cache"), 50_000_000))
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request())
                .newBuilder()
                .header("Cache-Control", cacheControl.toString())
                .build()
        }.build()

    private val gson = Gson()

    override fun hasPermission(player: UUID, node: String): Boolean? {
        val request = Request.Builder()
            .url("$baseUrl/user/$player/permission-check?permission=$node")
            .get()
            .build()
        val reply = client.newCall(request).execute()
        val str = reply.body?.string()
        return when (val result = gson.fromJson(str, JsonObject::class.java).get("result").asString) {
            "undefined" -> null
            "false" -> false
            "true" -> true
            else -> error("Unexpected reply: $result")
        }
    }

    override fun getMetadata(player: UUID): PlayerMeta {
        val request = Request.Builder()
            .url("$baseUrl/user/$player/meta")
            .get()
            .build()
        val reply = client.newCall(request).execute().body?.string()
        val obj = gson.fromJson(reply, JsonObject::class.java)
        val meta = obj.get("meta").asJsonObject
        return PlayerMeta(
            prefix = obj.getOrElse("prefix", { miniMessage.deserialize(it.asString) }, Component.empty()),
            suffix = obj.getOrElse("suffix", { miniMessage.deserialize(it.asString) }, Component.empty()),
            primaryGroup = meta.get("primarygroup")?.asString ?: "default",
            rankColor = meta.getOrElse(
                "rankcolor",
                { TextColor.fromHexString(it.asString) ?: NamedTextColor.GRAY },
                NamedTextColor.GRAY
            )
        )
    }

    private fun <T : Any> JsonObject.getOrElse(key: String, mapper: (JsonElement) -> T, default: T): T {
        if (has(key)) return mapper(get(key))
        return default
    }
}