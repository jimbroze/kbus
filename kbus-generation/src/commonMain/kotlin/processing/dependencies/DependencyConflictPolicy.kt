package com.jimbroze.kbus.generation.processing.dependencies

import com.jimbroze.kbus.generation.processing.ConflictPolicy

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
