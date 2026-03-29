package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.domain.event.DomainEventPublisher

data class CommandDependencies(val domainEventPublisher: DomainEventPublisher)
