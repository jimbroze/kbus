package com.jimbroze.kbus.core.uow

import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.messages.event.dispatch.DomainEventDispatcher
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventPublisher

class InvocationDomainEventPublisher(
    val domainEventDispatcher: DomainEventDispatcher,
    val invocation: CommandInvocation<*>,
) : DomainEventPublisher {
    override suspend fun publish(event: DomainEvent) {
        domainEventDispatcher.dispatchDomainEvent(event, invocation)
    }
}
