package com.bluedragonmc.server

import com.bluedragonmc.server.module.GameModule
import net.minestom.server.event.Event
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.reflect.KClass

abstract class ModuleHolder {

    private val logger = LoggerFactory.getLogger(ModuleHolder::class.java)

    data class WaitingGameModule<T : GameModule>(val module: T, val filter: Predicate<Event>, val callback: Consumer<T>)

    /**
     * A list of modules that have been added with the [use] method, but their dependencies have not yet been registered.
     */
    private val waiting = mutableListOf<WaitingGameModule<*>>()

    /**
     * A list of modules that have been loaded and subscribed to an event node.
     */
    val modules: MutableList<GameModule> = CopyOnWriteArrayList()

    private fun <T : GameModule> hasModule(type: KClass<T>): Boolean = modules.any { type.isInstance(it) }
    inline fun <reified T : GameModule> hasModule(): Boolean = modules.any { it is T }

    inline fun <reified T : GameModule> getModule(): T {
        return getModuleOrNull() ?: error("No module found of type ${T::class.simpleName} on game $this.")
    }

    inline fun <reified T : GameModule> getModuleOrNull(): T? {
        for (module in modules) {
            if (module is T) return module
        }
        return null
    }

    abstract fun <T : GameModule> register(module: T, filter: Predicate<Event>)

    private fun <T : GameModule> add(
        module: T,
        filter: Predicate<Event> = Predicate { true },
        callback: Consumer<T> = Consumer { },
    ) {
        waiting.removeIf { it.module == module }

        // Before adding this dependency, add any of its soft dependencies (if present)
        for ((waitingModule, waitingFilter, waitingCallback) in waiting) {
            if (module.getSoftDependencies().any { it.isInstance(waitingModule) }) {
                add(waitingModule, waitingFilter, waitingCallback as Consumer<GameModule>)
            }
        }

        logger.debug("Registering ${module::class.simpleName}")
        register(module, filter)
        modules.add(module)
        callback.accept(module)

        // If registering this dependency allows others to register, process those now
        for ((waitingModule, waitingFilter, waitingCallback) in ArrayList(waiting)) {
            if (dependenciesMet(waitingModule, true)) {
                logger.debug("${waitingModule::class.simpleName} is ready!")
                use(waitingModule, waitingFilter, waitingCallback as Consumer<GameModule>)
            }
        }
    }

    fun <T : GameModule> use(
        module: T,
        filter: Predicate<Event> = Predicate { true },
        callback: Consumer<T> = Consumer { },
    ) {
        // Make sure this module isn't accidentally being added twice
        if (modules.contains(module)) {
            return
        }

        // Ensure this module doesn't depend on itself
        if (module.getDependencies().any { it.isInstance(module) })
            throw IllegalStateException("Tried to register module which depends on itself: $module")

        waiting.removeIf { it.module == module }
        if (dependenciesMet(module, true)) {
            add(module, filter, callback)
        } else {
            logger.debug("Waiting for dependencies of ${module::class.simpleName}")
            waiting.add(WaitingGameModule(module, filter, callback))
        }
    }

    fun checkUnmetDependencies() {

        logger.debug("The following dependencies are still waiting: {}", waiting.map { it.module::class.simpleName })

        for ((module, filter, callback) in ArrayList(waiting)) {
            if (waiting.any { it.module == module } && dependenciesMet(module, includeSoftDeps = false)) {
                logger.debug("Adding ${module::class.simpleName} without its soft dependencies")
                add(module, filter, callback as Consumer<GameModule>)
            }
        }

        if (waiting.isNotEmpty()) {
            error("The following required game modules did not have their dependencies met: ${waiting.map { it.module::class.simpleName }}")
        }
    }

    private fun dependenciesMet(module: GameModule, includeSoftDeps: Boolean): Boolean {
        val depList = if (includeSoftDeps) module.getDependencies() else module.getRequiredDependencies()

        return depList.all { dep ->
            modules.any {
                dep.isInstance(it)
            }
        }
    }
}