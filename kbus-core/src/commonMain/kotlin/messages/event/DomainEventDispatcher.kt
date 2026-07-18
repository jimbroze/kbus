package com.jimbroze.kbus.core.messages.event

import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.domain.event.DomainEvent

interface DomainEventDispatcher {
    suspend fun <TEvent : DomainEvent> dispatchDomainEvent(
        event: TEvent,
        invocation: CommandInvocation<*>,
    )
}
