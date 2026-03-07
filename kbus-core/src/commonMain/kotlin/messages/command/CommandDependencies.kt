package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.domain.DomainEventPublisher

data class CommandDependencies(val domainEventPublisher: DomainEventPublisher)
