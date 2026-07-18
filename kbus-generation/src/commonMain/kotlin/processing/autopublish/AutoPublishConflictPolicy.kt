package com.jimbroze.kbus.generation.processing.autopublish

import com.jimbroze.kbus.generation.processing.ConflictPolicy

object AutoPublishConflictPolicy : ConflictPolicy<AutoPublishDefinition> {
    override fun evaluate(
        newItem: AutoPublishDefinition,
        existingItems: Collection<AutoPublishDefinition>,
    ): ConflictPolicy.Result {
        val existingForSameIntegrationEvent =
            existingItems.find { it.integrationEventClass == newItem.integrationEventClass }
                ?: return ConflictPolicy.Result.Accept

        return if (existingForSameIntegrationEvent == newItem) {
            ConflictPolicy.Result.ExactDuplicate
        } else {
            ConflictPolicy.Result.InvalidConflict(
                "Integration event ${newItem.integrationEventClass.simpleName} is auto-published " +
                    "from multiple domain events: " +
                    "'${existingForSameIntegrationEvent.domainEventClass.simpleName}' & " +
                    "'${newItem.domainEventClass.simpleName}'"
            )
        }
    }
}
