package com.jimbroze.kbus.core.messages.event.dispatch

import com.jimbroze.kbus.contracts.messages.event.EventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.EmptyIntegrationEventPublisher
import com.jimbroze.kbus.core.fixtures.TestUnitOfWork
import com.jimbroze.kbus.core.fixtures.emptyContextFactory
import com.jimbroze.kbus.core.fixtures.testInvocation
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import kotlinx.coroutines.test.TestScope

internal class EventDispatchEnvironment(
    val scope: TestScope,
    publisher: IntegrationEventPublisher = EmptyIntegrationEventPublisher,
) {
    val results = mutableListOf<String>()
    val unitOfWork = TestUnitOfWork<Any?>()
    val invocation = testInvocation(unitOfWork, publisher = publisher)
    lateinit var dispatcher: EventDispatcher

    fun withDomainHandlers(vararg handlers: DomainEventHandler<*>): EventDispatchEnvironment {
        @Suppress("UNCHECKED_CAST")
        val castedHandlers = handlers.toList() as List<DomainEventHandler<DomainEvent>>
        dispatcher =
            EventDispatcher(
                { _, _ -> castedHandlers },
                emptyList(),
                dispatcherScope = scope,
                contextFactory = emptyContextFactory(scope.backgroundScope),
            )
        return this
    }

    suspend fun dispatch(event: DomainEvent) = dispatcher.dispatchDomainEvent(event, invocation)

    suspend fun dispatchIntegration(event: IntegrationEvent, vararg handlers: EventHandler<*>) {
        @Suppress("UNCHECKED_CAST")
        val castedHandlers = handlers.toList() as List<EventHandler<IntegrationEvent>>
        dispatcher =
            EventDispatcher(
                { _, _ -> emptyList() },
                emptyList(),
                dispatcherScope = scope,
                contextFactory = emptyContextFactory(scope.backgroundScope),
            )
        dispatcher.dispatchIntegrationEvent(event, { castedHandlers })
    }

    suspend fun flushSecondaryWork() = unitOfWork.secondaryWork.forEach { it.invoke() }

    suspend fun flushPostCommitWork() = unitOfWork.postCommitWork.forEach { it.invoke() }

    suspend fun flushAllScheduledWork() = unitOfWork.executeAllScheduledWork()
}
