package com.jimbroze.kbus.application.messages.event

import com.jimbroze.kbus.api.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.application.messages.HandlerDependencies

data class EventHandlerDependencies(
    override val integrationEventPublisher: IntegrationEventPublisher
) : HandlerDependencies
