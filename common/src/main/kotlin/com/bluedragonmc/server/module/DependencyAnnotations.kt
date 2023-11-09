package com.bluedragonmc.server.module

import kotlin.reflect.KClass

/**
 * An annotation used to define module dependencies.
 * All modules specified in the [dependencies] parameter
 * will be initialized before the module which is being
 * annotated, and an error will be thrown if the
 * dependencies were not found.
 */
@Target(AnnotationTarget.CLASS)
annotation class DependsOn(vararg val dependencies: KClass<out GameModule>)

/**
 * An annotation used to define "soft dependencies".
 * All modules specified in the [dependencies] parameter
 * will be initialized before the module which is being
 * annotated.
 *
 * Unlike [DependsOn], an error will not be thrown if
 * a soft dependency is not found.
 */
@Target(AnnotationTarget.CLASS)
annotation class SoftDependsOn(vararg val dependencies: KClass<out GameModule>)
