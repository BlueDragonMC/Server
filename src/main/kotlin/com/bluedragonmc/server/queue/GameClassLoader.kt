package com.bluedragonmc.server.queue

import java.net.URL
import java.net.URLClassLoader

/**
 * A class loader that loads all JAR files in a given directory.
 */
class GameClassLoader(urls: Array<out URL>?) : URLClassLoader(urls) {

    init {
        loaders.add(this)
    }

    override fun close() {
        loaders.remove(this)
        super.close()
    }

    /**
     * Overrides [URLClassLoader] to prioritize resources in the
     * URL list over resources found by the parent [ClassLoader].
     */
    override fun getResource(name: String?): URL? {
        findResource(name)?.let { return it }
        return super.getResource(name)
    }

    companion object {
        internal val loaders = mutableListOf<GameClassLoader>()
    }
}