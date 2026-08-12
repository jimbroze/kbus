package com.jimbroze.kbus.core.module

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.core.fixtures.OtherStorageEvent
import com.jimbroze.kbus.core.fixtures.OtherStorageEventHandler
import com.jimbroze.kbus.core.fixtures.PrintEventHandler
import com.jimbroze.kbus.core.fixtures.RecordingInboxStore
import com.jimbroze.kbus.core.fixtures.StorageEvent
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.ThrowingIntegrationEventHandler
import com.jimbroze.kbus.core.fixtures.emptyContextFactory
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.core.messages.event.routing.AggregateException
import com.jimbroze.kbus.core.module.inbox.BoundedContextInbox
import com.jimbroze.kbus.core.module.inbox.InboxAckPolicy
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ContextRuntimeTest {
    private fun createRuntime(
        locator: PersistingHandlerLocator,
        dispatcherScope: CoroutineScope,
        inbox: BoundedContextInbox? = null,
    ): ContextRuntime {
        val eventDispatcher =
            EventDispatcher(
                locator::domainHandlersFor,
                emptyList(),
                dispatcherScope,
                contextFactory = emptyContextFactory(dispatcherScope),
            )
        return ContextRuntime(
            BoundedContext(BoundedContextId.DEFAULT, locator, inbox),
            eventDispatcher = lazy { eventDispatcher },
        )
    }

    @Test
    fun `dispatches each delivered envelope to its handlers in order`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val results = mutableListOf<String>()

        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )

        val runtime = createRuntime(locator, this)

        runtime.deliver(
            listOf(
                EventEnvelope.of(StorageEvent("first", results)),
                EventEnvelope.of(StorageEvent("second", results)),
            )
        )
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), results)
    }

    @Test
    fun `dispatches nothing for an event it has no handler for`() = runTest {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val runtime = createRuntime(locator, this)

        runtime.deliver(listOf(EventEnvelope.of(TestIntegrationEvent("unhandled"))))
        advanceUntilIdle()
    }

    @Test
    fun `dispatches nothing for an empty delivery`() = runTest {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val runtime = createRuntime(locator, this)

        runtime.deliver(emptyList())
        advanceUntilIdle()
    }

    private fun registerStorageEventHandler(stores: HandlerFactoryStoreCollection) {
        stores.eventStore.registerHandlers(
            StorageEvent::class,
            listOf(EventHandlerFactory(PrintEventHandler::class) { PrintEventHandler() }),
        )
    }

    @Test
    fun `accepts an event it has an integration handler for`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        registerStorageEventHandler(stores)
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )

        val runtime = createRuntime(locator, this)

        assertTrue(runtime.appliesTo(StorageEvent("any", mutableListOf())))
    }

    @Test
    fun `refuses an event it has no handler for`() = runTest {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val runtime = createRuntime(locator, this)

        assertFalse(runtime.appliesTo(StorageEvent("any", mutableListOf())))
    }

    /**
     * Subscriptions are read once, when the runtime is built. Registering through a retained
     * locator is the one way to get a handler in after that, and it is deliberately not honoured —
     * `BoundedContext`'s registration lambda is the supported path, and it runs before any runtime
     * exists.
     */
    @Test
    fun `ignores a handler registered after it was constructed`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val runtime = createRuntime(locator, this)

        registerStorageEventHandler(stores)
        locator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )

        assertFalse(runtime.appliesTo(StorageEvent("any", mutableListOf())))
    }

    @Test
    fun `refuses another context's event even when they share handler stores`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        registerStorageEventHandler(stores)
        stores.eventStore.registerHandlers(
            OtherStorageEvent::class,
            listOf(
                EventHandlerFactory(OtherStorageEventHandler::class) { OtherStorageEventHandler() }
            ),
        )

        val thisLocator = PersistingHandlerLocator(stores)
        thisLocator.integrationEventMapper.addEventHandlers(
            StorageEvent::class,
            listOf(PrintEventHandler::class),
        )
        val otherLocator = PersistingHandlerLocator(stores)
        otherLocator.integrationEventMapper.addEventHandlers(
            OtherStorageEvent::class,
            listOf(OtherStorageEventHandler::class),
        )

        val runtime = createRuntime(thisLocator, this)

        assertTrue(runtime.appliesTo(StorageEvent("any", mutableListOf())))
        assertFalse(runtime.appliesTo(OtherStorageEvent("any", mutableListOf())))
    }

    @Test
    fun `applies the acknowledgement policy its own context declares`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val attempts = mutableListOf<String>()
        stores.eventStore.registerHandlers(
            TestIntegrationEvent::class,
            listOf(
                EventHandlerFactory(ThrowingIntegrationEventHandler::class) {
                    ThrowingIntegrationEventHandler(attempts)
                }
            ),
        )
        locator.integrationEventMapper.addEventHandlers(
            TestIntegrationEvent::class,
            listOf(ThrowingIntegrationEventHandler::class),
        )

        val honouring = createRuntime(locator, this)
        val requiringSuccess =
            createRuntime(
                locator,
                this,
                BoundedContextInbox(RecordingInboxStore(), InboxAckPolicy.RequireHandlerSuccess),
            )

        // A context declaring no inbox honours the event's own FireAndForget: failure is swallowed.
        honouring.deliver(listOf(EventEnvelope.of(TestIntegrationEvent("first"))))
        advanceUntilIdle()
        assertEquals(listOf("first"), attempts)

        // RequireHandlerSuccess forces ContinueAndAggregate, so the failure now surfaces.
        assertFailsWith<AggregateException> {
            requiringSuccess.deliver(listOf(EventEnvelope.of(TestIntegrationEvent("second"))))
        }
        assertEquals(listOf("first", "second"), attempts)
    }
}
