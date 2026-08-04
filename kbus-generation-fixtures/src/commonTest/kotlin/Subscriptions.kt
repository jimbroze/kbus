package com.jimbroze.kbus.generation.test

import com.jimbroze.kbus.core.module.EventSubscription
import com.jimbroze.kbus.core.registry.generation.subscribe
import com.jimbroze.kbus.generated.loaded

val depotSubscriptions: List<EventSubscription<*>> =
    listOf(subscribe(ArrivalConfirmed::class, ConfirmArrivalHandler::class.loaded))

val defaultSubscriptions: List<EventSubscription<*>> =
    listOf(subscribe(TestShipmentIntegration::class, TestShipmentIntegrationHandler::class.loaded))
