package com.bluedragonmc.server.shutdown

import net.kyori.adventure.text.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A server-wide shutdown notice that games can display on their sidebars.
 */
object ServerShutdownNotice {

    data class Notice(val line: Component)

    @Volatile
    var current: Notice? = null
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun update(line: Component) {
        current = Notice(line)
        notifyListeners()
    }

    fun clear() {
        current = null
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { listener -> runCatching { listener() } }
    }
}
