package com.jimbroze.kbus.generation.processing

interface ConflictPolicy<T> {
    sealed class Result {
        object Accept : Result()

        object ExactDuplicate : Result()

        data class InvalidConflict(val reason: String) : Result()
    }

    fun evaluate(newItem: T, existingItems: Collection<T>): Result
}
