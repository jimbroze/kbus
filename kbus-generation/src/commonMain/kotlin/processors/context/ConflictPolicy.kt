package com.jimbroze.kbus.generation.processors.context

import com.jimbroze.kbus.generation.processing.dependencies.DependencyWithChildren
import com.jimbroze.kbus.generation.processing.handlers.HandlerDefinition

interface ConflictPolicy<T> {
    sealed class Result {
        object Accept : Result()

        object ExactDuplicate : Result()

        data class InvalidConflict(val reason: String) : Result()
    }

    fun evaluate(newItem: T, existingItems: Collection<T>): Result
}

object HandlerConflictPolicy : ConflictPolicy<HandlerDefinition> {
    override fun evaluate(
        newItem: HandlerDefinition,
        existingItems: Collection<HandlerDefinition>,
    ): ConflictPolicy.Result {
        val existingWithSameMessage =
            existingItems.filter { it.handlerData.messageClass == newItem.handlerData.messageClass }

        if (existingWithSameMessage.isEmpty()) {
            return ConflictPolicy.Result.Accept
        }

        val existingWithDifferentHandler =
            existingWithSameMessage.firstOrNull { existing ->
                existing.handlerData.handlerClass != newItem.handlerData.handlerClass
            }
        return if (existingWithDifferentHandler !== null) {
            val messageClassName = newItem.handlerData.messageClass.simpleName
            val oldHandlerName = existingWithDifferentHandler.handlerData.handlerClass.simpleName
            val newHandlerName = newItem.handlerData.handlerClass.simpleName

            ConflictPolicy.Result.InvalidConflict(
                "Message class $messageClassName is used by multiple handlers: " +
                    "'$oldHandlerName' & '$newHandlerName'"
            )
        } else {
            ConflictPolicy.Result.ExactDuplicate
        }
    }
}

object DependencyConflictPolicy : ConflictPolicy<DependencyWithChildren> {
    override fun evaluate(
        newItem: DependencyWithChildren,
        existingItems: Collection<DependencyWithChildren>,
    ): ConflictPolicy.Result {
        return existingItems.firstNotNullOfOrNull { existing ->
            when {
                newItem == existing -> ConflictPolicy.Result.ExactDuplicate

                newItem.metadata.hasConflictingNameWith(existing.metadata) ->
                    ConflictPolicy.Result.InvalidConflict(
                        "Tried to generate multiple dependencies for ${newItem.metadata.name}"
                    )

                else -> null
            }
        } ?: ConflictPolicy.Result.Accept
    }
}
