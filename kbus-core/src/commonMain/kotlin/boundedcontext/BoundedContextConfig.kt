package com.jimbroze.kbus.core.boundedcontext

import com.jimbroze.kbus.core.boundedcontext.inbox.BoundedContextInbox

/**
 * What a generated bus needs to finish building one of its bounded contexts. A generated bus takes
 * one of these per context, under that context's own parameter name, so a context that does not
 * exist cannot be named.
 */
class BoundedContextConfig(
    val inbox: BoundedContextInbox? = null,
    val domainSubscriptions: List<DomainEventSubscription<*>> = emptyList(),
    val integrationSubscriptions: List<IntegrationEventSubscription<*>> = emptyList(),
)
