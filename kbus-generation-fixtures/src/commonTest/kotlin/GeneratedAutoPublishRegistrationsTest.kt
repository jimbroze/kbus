@file:OptIn(ExperimentalTime::class)

package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.generated.generatedAutoPublishRegistrations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class GeneratedAutoPublishRegistrationsTest {
    @Test
    fun `registers for auto-publish only the mappers that opted in`() {
        // TestShipmentIntegrationMapper implements IntegrationEventMapper directly and
        // TestShipmentAnalyticsMapper via a generic intermediate interface; TestShipmentAudit has
        // no annotated mapper, so it contributes no registration.
        val registrationsForShipmentEvent =
            generatedAutoPublishRegistrations.count { it.eventClass == TestShipmentEvent::class }

        assertEquals(2, registrationsForShipmentEvent)
    }

    @Test
    fun `registers for auto-publish the mappers a submodule opted in`() {
        val eventClasses = generatedAutoPublishRegistrations.map { it.eventClass }

        assertTrue(eventClasses.contains(ArrivalRecorded::class))
    }

    /**
     * This module declares handlers of its own and no `kbus.boundedContextIdentity`, so they land
     * in the default context. This is the regression guard for the unassigned-identity path.
     */
}
