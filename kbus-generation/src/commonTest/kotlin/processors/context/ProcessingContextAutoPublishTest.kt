package com.jimbroze.kbus.generation.processors.context

import com.jimbroze.kbus.generation.processing.ConflictPolicy
import com.jimbroze.kbus.generation.processing.autopublish.AutoPublishDefinition
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProcessingContextAutoPublishTest {
    private val domainEventOne = ClassName("com.example", "OrderPlaced")
    private val domainEventTwo = ClassName("com.example", "OrderCancelled")
    private val integrationEventOne = ClassName("com.example", "OrderPlacedIntegration")
    private val integrationEventTwo = ClassName("com.example", "OrderPlacedAnalytics")

    @Test
    fun new_definition_returns_accept() {
        val context = ProcessingContext()

        val result =
            context.tryAddAutoPublish(AutoPublishDefinition(integrationEventOne, domainEventOne))

        assertIs<ConflictPolicy.Result.Accept>(result)
        assertTrue(context.hasAutoPublish(integrationEventOne))
    }

    @Test
    fun identical_definition_returns_exact_duplicate() {
        val context = ProcessingContext()
        context.tryAddAutoPublish(AutoPublishDefinition(integrationEventOne, domainEventOne))

        val result =
            context.tryAddAutoPublish(AutoPublishDefinition(integrationEventOne, domainEventOne))

        assertIs<ConflictPolicy.Result.ExactDuplicate>(result)
    }

    @Test
    fun same_integration_event_different_domain_event_returns_invalid_conflict() {
        val context = ProcessingContext()
        context.tryAddAutoPublish(AutoPublishDefinition(integrationEventOne, domainEventOne))

        val result =
            context.tryAddAutoPublish(AutoPublishDefinition(integrationEventOne, domainEventTwo))

        assertIs<ConflictPolicy.Result.InvalidConflict>(result)
    }

    @Test
    fun different_integration_events_from_same_domain_event_both_accepted() {
        val context = ProcessingContext()
        val firstResult =
            context.tryAddAutoPublish(AutoPublishDefinition(integrationEventOne, domainEventOne))
        val secondResult =
            context.tryAddAutoPublish(AutoPublishDefinition(integrationEventTwo, domainEventOne))

        assertIs<ConflictPolicy.Result.Accept>(firstResult)
        assertIs<ConflictPolicy.Result.Accept>(secondResult)
        assertTrue(context.hasAutoPublish(integrationEventOne))
        assertTrue(context.hasAutoPublish(integrationEventTwo))
    }

    @Test
    fun context_with_only_auto_publish_definitions_is_not_empty() {
        val context = ProcessingContext()

        context.tryAddAutoPublish(AutoPublishDefinition(integrationEventOne, domainEventOne))

        assertTrue(!context.isEmpty())
    }
}
