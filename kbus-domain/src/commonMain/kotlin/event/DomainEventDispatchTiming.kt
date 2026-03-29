package com.jimbroze.kbus.domain.event

sealed interface DomainEventDispatchTiming {
    data object Immediately : DomainEventDispatchTiming

    data object AfterPrimaryWork : DomainEventDispatchTiming

    data object AfterTransaction : DomainEventDispatchTiming
}
