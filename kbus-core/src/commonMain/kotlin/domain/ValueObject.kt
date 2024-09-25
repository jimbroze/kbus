package com.jimbroze.kbus.core.domain

abstract class ValueObject<T : ValueObject<T>> : HasInvariants() {
    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    fun hasSameValueAs(other: T): Boolean {
        return equals(other)
    }
}
