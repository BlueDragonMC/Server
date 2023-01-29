package com.bluedragonmc.server.utils

import com.bluedragonmc.server.module.GameModule
import net.minestom.server.event.Event
import java.util.function.Predicate
import kotlin.reflect.KClass

interface ModuleDependency<T : GameModule> {
    val type: KClass<T>
}

data class FilledModuleDependency<T : GameModule>(
    override val type: KClass<T>,
    val instance: GameModule,
    /**
     * The event filter can be null when the module is already registered, meaning
     * this [FilledModuleDependency] instance represents a module that's been
     * registered in another part of the tree.
     */
    val eventFilter: Predicate<Event>?,
) :
    ModuleDependency<T> {
    override fun toString(): String {
        instance::class.simpleName?.let { className ->
            return className + "@" + instance.hashCode()
        }
        return instance.toString()
    }
}

data class EmptyModuleDependency<T : GameModule>(override val type: KClass<T>) : ModuleDependency<T> {
    override fun toString(): String = "[" + (type.simpleName ?: type.toString()) + "]"
}
