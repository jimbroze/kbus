package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.module.IntegrationEventSubscription
import com.jimbroze.kbus.core.registry.generation.integrationSubscription
import com.jimbroze.kbus.generated.loaded

val depotSubscriptions: List<IntegrationEventSubscription<*>> =
    listOf(integrationSubscription(ArrivalConfirmed::class, ConfirmArrivalHandler::class.loaded))

val defaultSubscriptions: List<IntegrationEventSubscription<*>> =
    listOf(
        integrationSubscription(
            TestShipmentIntegration::class,
            TestShipmentIntegrationHandler::class.loaded,
        )
    )
