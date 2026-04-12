package com.jimbroze.kbus.domain

abstract class ValueObject<T : ValueObject<T>> {
    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    fun hasSameValueAs(other: T): Boolean {
        return equals(other)
    }
}
