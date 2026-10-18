package com.jimbroze.kbus.validation

interface ValidationRule<in T, out E> {
    val failsWith: E

    fun isSatisfiedBy(value: T): Boolean
}
