package com.jimbroze.kbus.core.fixtures

import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class EmptyIntegrationEventPublisherTest {
    @Test
    fun `accepts a publish and does nothing with it`() = runTest {
        EmptyIntegrationEventPublisher.publish(listOf(TestIntegrationEvent("ignored")))
    }
}
