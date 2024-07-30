package com.jimbroze.kbus.core.domain

open class InvalidInvariantException(override val message: String) : RuntimeException(message)

abstract class HasInvariants {
    protected fun assert(invariant: Boolean, message: String) {
        if (!invariant) throw InvalidInvariantException(message)
    }
    protected fun assert(invariant: Boolean, exception: InvalidInvariantException) {
        if (!invariant) throw exception
    }
}
