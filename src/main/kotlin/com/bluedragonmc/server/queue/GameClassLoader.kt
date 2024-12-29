package com.bluedragonmc.server.queue

import java.net.URL
import java.net.URLClassLoader

/**
 * A class loader that loads all JAR files in a given directory.
 */
class GameClassLoader(urls: Array<out URL>?) : URLClassLoader(urls) {

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        return loadClass0(name, resolve, search = true)
    }

    private fun loadClass0(name: String?, resolve: Boolean, search: Boolean): Class<*> {
        val ex: ClassNotFoundException
        try {
            return super.loadClass(name, resolve)
        } catch (classNotFoundException: ClassNotFoundException) {
            // Ignored
            ex = classNotFoundException
        }
        if (!search) throw ex

        loaders.filter { it != this }.forEach { loader ->
            try {
                return loader.loadClass0(name, resolve, search = false)
            } catch (_: ClassNotFoundException) {
                // Ignored
            }
        }

        throw ex
    }

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
        internal val loaders = mutableSetOf<GameClassLoader>()
    }
}