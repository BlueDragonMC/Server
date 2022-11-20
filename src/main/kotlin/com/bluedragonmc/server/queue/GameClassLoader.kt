package com.bluedragonmc.server.queue

import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries

/**
 * A class loader that loads all JAR files in a given directory.
 */
class GameClassLoader(folder: Path) : URLClassLoader(getUrls(folder)) {

    companion object {
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