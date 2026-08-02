package com.jimbroze.kbus.core.messages.command

import com.jimbroze.kbus.domain.event.DomainEventPublisher

/**
 * What a command handler is given for the duration of one command's execution: everything scoped to
 * the invocation rather than to the handler itself.
 */
data class CommandDependencies(
    val domainEventPublisher: DomainEventPublisher,
    val commandExecutor: NestedCommandExecutor,
)
