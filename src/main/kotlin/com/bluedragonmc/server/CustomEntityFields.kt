package com.bluedragonmc.server

import net.minestom.server.entity.LivingEntity
import kotlin.reflect.KProperty

var LivingEntity.hurtResistantTime by PerEntityField(0)
val LivingEntity.maxHurtResistantTime by PerEntityField(20)
var LivingEntity.lastDamage by PerEntityField(0.0f)

class PerEntityField<out T : Any>(private val defaultValue: T) {

    private val map = hashMapOf<Any, T?>()

    operator fun getValue(thisRef: Any, property: KProperty<*>): T = map[thisRef] ?: defaultValue

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: Any?) {
        map[thisRef] = value as T?
    }
}