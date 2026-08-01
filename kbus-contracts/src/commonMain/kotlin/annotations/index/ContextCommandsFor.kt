package com.jimbroze.kbus.contracts.annotations.index

/**
 * Stamped on a generated per-context command executor interface, naming the bounded context it
 * covers. It is how a downstream module learns which context an interface belongs to, since every
 * module's generated types share one package and only the identity distinguishes them.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ContextCommandsFor(val contextIdentity: String)
