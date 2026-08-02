package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.messages.HandlerDependencies

data class EventHandlerDependencies(
    override val integrationEventPublisher: IntegrationEventPublisher
) : HandlerDependencies
