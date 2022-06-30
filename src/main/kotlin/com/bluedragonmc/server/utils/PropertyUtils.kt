package com.bluedragonmc.server.utils

import kotlin.reflect.KProperty

class SingleAssignmentProperty<out T> {

    private var value: T? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        require(value != null) { "Tried to access ${property.name} before it was initialized!" }
        return value!!
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Any?) {
        this.value = value as T
    }
}