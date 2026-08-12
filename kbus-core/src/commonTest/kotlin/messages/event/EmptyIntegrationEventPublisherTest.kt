package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.fixtures.EmptyIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class EmptyIntegrationEventPublisherTest {
    @Test
    fun `accepts a publish and does nothing with it`() = runTest {
        EmptyIntegrationEventPublisher.publish(listOf(TestIntegrationEvent("ignored")))
    }
}
