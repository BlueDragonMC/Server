package com.bluedragonmc.server

import com.bluedragonmc.server.module.GameModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.reflect.KClass

open class ModuleHolder {

    private val logger = LoggerFactory.getLogger(ModuleHolder::class.java)

    /**
     * The root event node that every module's child node is attached to.
     *
     * Callers hosting modules outside of a [Game] should add this node as a child
     * of their own event node. Subclasses (such as [Game]) may override this to
     * supply a filtered or otherwise customized event node.
     */
    open val rootEventNode: EventNode<Event> by lazy {
        EventNode.all("module-holder")
    }

    data class WaitingGameModule<T : GameModule>(val module: T, val filter: Predicate<Event>, val callback: Consumer<T>)

    /**
     * A list of modules that have been added with the [use] method, but their dependencies have not yet been registered.
     */
    private val waiting = mutableListOf<WaitingGameModule<*>>()

    /**
     * A list of modules that have been loaded and subscribed to an event node.
     */
    @PublishedApi
    internal val modules: MutableList<GameModule> = CopyOnWriteArrayList()

    /**
     * The simple names of every loaded module, in registration order.
     */
    fun getModuleNames(): List<String> = modules.map { it::class.simpleName.orEmpty() }

    /**
     * Unregisters the first loaded module whose simple class name matches [name].
     *
     * @return `true` if a module was found and unregistered.
     */
    fun unregisterModule(name: String): Boolean {
        val module = modules.firstOrNull { it::class.simpleName == name } ?: return false
        unregister(module)
        return true
    }

    private fun <T : GameModule> hasModule(type: KClass<T>): Boolean = modules.any { type.isInstance(it) }
    inline fun <reified T : GameModule> hasModule(): Boolean = modules.any { it is T }

    /**
     * Looks up a loaded module by type, without any dependency validation.
     *
     * Only this holder's own internals, [Game], and other trusted non-module callers should use this.
     * [GameModule]s must use their dependency-checked [GameModule.getModule]/[GameModule.getModuleOrNull].
     */
    @Suppress("UNCHECKED_CAST")
    @PublishedApi
    internal fun <T : GameModule> findModule(type: KClass<T>): T? =
        modules.firstOrNull { type.isInstance(it) } as T?

    @PublishedApi
    internal fun <T : GameModule> requireModule(type: KClass<T>): T =
        findModule(type) ?: error("No module found of type ${type.simpleName} on game $this.")

    open fun <T : GameModule> register(module: T, filter: Predicate<Event>) {
        module.attach(this)
        val node = EventNode.event(module::class.simpleName.orEmpty(), EventFilter.ALL, filter)
        node.priority = module.eventPriority
        rootEventNode.addChild(node)
        module.eventNode = node
        module.initialize(this, node)
    }

    open fun unregister(module: GameModule) {
        logger.debug("Unregistering module {}", module)
        module.deinitialize()
        modules.remove(module)
        val node = module.eventNode
        node.parent?.removeChild(node)
    }

    /**
     * Dispatches [event] on this holder's root event node.
     */
    fun callEvent(event: Event) = rootEventNode.call(event)

    /**
     * Dispatches a cancellable [event], running [successCallback] if it was not cancelled.
     */
    fun callCancellable(event: Event, successCallback: Runnable) =
        rootEventNode.callCancellable(event, successCallback)

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