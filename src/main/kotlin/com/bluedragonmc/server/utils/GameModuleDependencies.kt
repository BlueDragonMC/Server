package com.bluedragonmc.server.utils

import com.bluedragonmc.server.module.GameModule
import kotlin.reflect.KClass

interface ModuleDependency<T : GameModule> {
    val type: KClass<T>
}

data class FilledModuleDependency<T : GameModule>(override val type: KClass<T>, val instance: GameModule) :
    ModuleDependency<T> {
    override fun toString(): String {
        instance::class.simpleName?.let { className ->
            return className + "@" + instance.hashCode()
        }
        return instance.toString()
    }
}

data class EmptyModuleDependency<T : GameModule>(override val type: KClass<T>) : ModuleDependency<T> {
    override fun toString(): String = type.simpleName ?: type.toString()
}
