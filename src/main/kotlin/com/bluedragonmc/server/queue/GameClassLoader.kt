package com.bluedragonmc.server.queue

import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries

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

    override fun close() {
        loaders.remove(this)
        super.close()
    }

    companion object {

        internal val loaders = mutableSetOf<GameClassLoader>()

        /**
         * Returns the URLs of all JAR files in the given folder.
         */
        private fun getUrls(folder: Path): Array<URL> {
            return folder.listDirectoryEntries()
                .filter { it.extension == "jar" }
                .map { it.toUri().toURL() }.toTypedArray()
        }
    }
}