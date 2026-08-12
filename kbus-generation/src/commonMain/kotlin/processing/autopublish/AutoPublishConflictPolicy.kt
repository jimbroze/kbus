package com.jimbroze.kbus.generation.processing.autopublish

import com.jimbroze.kbus.generation.processing.ConflictPolicy

object AutoPublishConflictPolicy : ConflictPolicy<AutoPublishDefinition> {
    override fun evaluate(
        newItem: AutoPublishDefinition,
        existingItems: Collection<AutoPublishDefinition>,
    ): ConflictPolicy.Result {
        val existingForSameMapper =
            existingItems.find { it.mapperClass == newItem.mapperClass }
                ?: return ConflictPolicy.Result.Accept

        return if (existingForSameMapper == newItem) {
            ConflictPolicy.Result.ExactDuplicate
        } else {
            ConflictPolicy.Result.InvalidConflict(
                "Mapper ${newItem.mapperClass.simpleName} maps from multiple domain events: " +
                    "'${existingForSameMapper.domainEventClass.simpleName}' & " +
                    "'${newItem.domainEventClass.simpleName}'"
            )
        }
    }
}
