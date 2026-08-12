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
    private val mapperOne = ClassName("com.example", "OrderPlacedMapper")
    private val mapperTwo = ClassName("com.example", "OrderPlacedAnalyticsMapper")

    @Test
    fun `accepts an auto-publish definition nothing is registered for`() {
        val context = ProcessingContext()

        val result = context.tryAddAutoPublish(AutoPublishDefinition(mapperOne, domainEventOne))

        assertIs<ConflictPolicy.Result.Accept>(result)
        assertTrue(context.hasAutoPublish(mapperOne))
    }

    @Test
    fun `reports an identical definition as an exact duplicate`() {
        val context = ProcessingContext()
        context.tryAddAutoPublish(AutoPublishDefinition(mapperOne, domainEventOne))

        val result = context.tryAddAutoPublish(AutoPublishDefinition(mapperOne, domainEventOne))

        assertIs<ConflictPolicy.Result.ExactDuplicate>(result)
    }

    @Test
    fun `reports one mapper mapping from two domain events as a conflict`() {
        val context = ProcessingContext()
        context.tryAddAutoPublish(AutoPublishDefinition(mapperOne, domainEventOne))

        val result = context.tryAddAutoPublish(AutoPublishDefinition(mapperOne, domainEventTwo))

        assertIs<ConflictPolicy.Result.InvalidConflict>(result)
    }

    @Test
    fun `accepts two mappers mapping from one domain event`() {
        val context = ProcessingContext()
        val firstResult =
            context.tryAddAutoPublish(AutoPublishDefinition(mapperOne, domainEventOne))
        val secondResult =
            context.tryAddAutoPublish(AutoPublishDefinition(mapperTwo, domainEventOne))

        assertIs<ConflictPolicy.Result.Accept>(firstResult)
        assertIs<ConflictPolicy.Result.Accept>(secondResult)
        assertTrue(context.hasAutoPublish(mapperOne))
        assertTrue(context.hasAutoPublish(mapperTwo))
    }

    @Test
    fun `counts a context holding only auto-publish definitions as non-empty`() {
        val context = ProcessingContext()

        context.tryAddAutoPublish(AutoPublishDefinition(mapperOne, domainEventOne))

        assertTrue(!context.isEmpty())
    }
}
