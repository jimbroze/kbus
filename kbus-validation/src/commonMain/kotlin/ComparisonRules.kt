package com.jimbroze.kbus.validation

class AtLeast<T : Comparable<T>, out E>(val minimum: T, override val failsWith: E) :
    ValidationRule<T, E> {
    constructor(minimum: T, failsWith: (minimum: T) -> E) : this(minimum, failsWith(minimum))

    override fun isSatisfiedBy(value: T) = value >= minimum
}

class AtMost<T : Comparable<T>, out E>(val maximum: T, override val failsWith: E) :
    ValidationRule<T, E> {
    constructor(maximum: T, failsWith: (maximum: T) -> E) : this(maximum, failsWith(maximum))

    override fun isSatisfiedBy(value: T) = value <= maximum
}

class GreaterThan<T : Comparable<T>, out E>(val exclusiveMinimum: T, override val failsWith: E) :
    ValidationRule<T, E> {
    constructor(
        exclusiveMinimum: T,
        failsWith: (exclusiveMinimum: T) -> E,
    ) : this(exclusiveMinimum, failsWith(exclusiveMinimum))

    override fun isSatisfiedBy(value: T) = value > exclusiveMinimum
}

class LessThan<T : Comparable<T>, out E>(val exclusiveMaximum: T, override val failsWith: E) :
    ValidationRule<T, E> {
    constructor(
        exclusiveMaximum: T,
        failsWith: (exclusiveMaximum: T) -> E,
    ) : this(exclusiveMaximum, failsWith(exclusiveMaximum))

    override fun isSatisfiedBy(value: T) = value < exclusiveMaximum
}
