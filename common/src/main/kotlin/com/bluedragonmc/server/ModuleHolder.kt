package com.bluedragonmc.server

import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.*
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

abstract class ModuleHolder {

    private val logger = LoggerFactory.getLogger(ModuleHolder::class.java)

    /**
     * A list of modules that have been added with the [use] method, but their dependencies have not been added.
     */
    protected val dependencyTree = Root<ModuleDependency<*>>()

    /**
     * A list of modules that have been loaded and subscribed to an event node.
     */
    val modules = mutableListOf<GameModule>()

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

    /**
     * When a new [module] is added, this method should be called
     * to check if any other modules' dependencies have been solved
     * by adding the new module. If so, the dependencies will be
     * marked as solved (filled) and, if all dependencies have been
     * met for any of the modules, they will be registered.
     */
    private fun solveDependencies(module: GameModule) {
        // Get a list of dependencies on the bottom level of the dependency tree
        val depth = dependencyTree.maxDepth()
        if (depth == 0) return
        val entries = dependencyTree.elementsAtDepth(depth)
        // For every entry, check if its dependencies have been solved by adding the `module`
        entries.forEach { node ->
            // If this dependency is empty, and it would be solved by adding `module`
            if (node.value is EmptyModuleDependency && node.value!!.type.isInstance(module)) {
                // This dependency has been solved
                logger.debug("Dependency [${node.value!!.type}] of module ${node.parent.value} SOLVED with $module")
                node.value = FilledModuleDependency(node.value!!.type, module)
                // If all dependencies have been filled, use this module
                if (node.parent.value is FilledModuleDependency && canAddModule(module)) {
                    val parentModule = (node.parent.value as FilledModuleDependency<*>).instance
                    logger.debug("Using module because its dependencies have been solved: $parentModule")
                    use(parentModule)
                }
            }
        }
    }

    /**
     * Adds a module to the dependency tree for this instance.
     * The module may or not be registered immediately, depending
     * on if its dependencies have been filled.
     */
    fun <T : GameModule> use(module: T): T {

        logger.debug("Attempting to register module $module")
        // Ensure this module has not been registered already
        if (hasModule(module::class))
            throw IllegalStateException("Tried to register module that is already registered: $module")
        // Ensure this module does not depend on itself
        if (getDependencies(module).any { it.isInstance(module) })
            throw IllegalStateException("Tried to register module which depends on itself: $module")

        // Create a node in the dependency tree for this module if it doesn't already exist
        if (dependencyTree.getChildren().none { it.value!!.type == module::class }) {
            addNode(module)
        }

        // If not all the module's dependencies were found, delay the loading of
        // the module until after all of its dependencies have been registered.
        if (canAddModule(module)) {
            register(module)
            modules.add(module)
            solveDependencies(module)
        } else {
            logger.debug("Waiting for dependencies of module $module to load before registering.")
        }

        return module // Return the module for method chaining
    }

    /**
     * Uses all the modules in the list, then
     * checks for unmet module dependencies.
     */
    fun useModules(modules: Iterable<GameModule>) {
        modules.forEach(::use)
        checkUnmetDependencies()
    }

    abstract fun <T : GameModule> register(module: T)

    fun checkUnmetDependencies() {
        dependencyTree.dfs { node, _ ->
            if (node.value is EmptyModuleDependency<*>) {
                throw IllegalStateException("Unmet dependency: ${node.value?.type} was not found, but is required by ${node.parent.value?.type}.")
            }
        }
    }

    /**
     * Create a node for the [module], populate it with
     * the module's dependencies, and add it to the
     * dependency tree.
     */
    private fun addNode(module: GameModule) {
        val moduleDependencyNode = Node<ModuleDependency<*>>(FilledModuleDependency(module::class, module))
        // Add this module's dependencies as children in its branch of the tree
        moduleDependencyNode.addChildren(getDependencies(module).map { EmptyModuleDependency(it) })
        // Fill all dependencies which have already been registered.
        fillDependencies(moduleDependencyNode)
        // Add the node to the tree
        dependencyTree.addChild(moduleDependencyNode)
        logger.trace("Added node to dependency tree: node: $moduleDependencyNode")
    }

    private fun fillDependencies(node: Node<ModuleDependency<*>>) {
        node.getChildren().forEach { childNode ->
            modules.firstOrNull { module -> childNode.value?.type?.isInstance(module) == true }?.let { found ->
                childNode.value = FilledModuleDependency(found::class, found)
            }
        }
    }

    /**
     * Determines whether the module can be added now, or if its
     * dependencies must be loaded first.
     * @return true if the module can be immediately loaded
     */
    private fun canAddModule(module: GameModule): Boolean = getDependencies(module).all { type ->
        // Look for filled module dependencies at the root of the tree
        modules.any { module ->
            // If the node is a filled dependency, check if its type corresponds with the given parameter's type
            type.isInstance(module)
        }
    }

    private fun getDependencies(module: GameModule) = module::class.findAnnotation<DependsOn>()?.dependencies ?: emptyArray()
}
