package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.messages.event.DomainEventDispatcher
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventPublisher

class InvocationDomainEventPublisher(
    val baseDispatcher: DomainEventDispatcher?,
    val invocation: CommandInvocation<*>,
) : DomainEventPublisher {
    override suspend fun publish(event: DomainEvent) {
        baseDispatcher?.dispatchDomainEvent(event, invocation)
    }
}
