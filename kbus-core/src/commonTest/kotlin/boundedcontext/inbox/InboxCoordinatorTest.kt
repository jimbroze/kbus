package com.jimbroze.kbus.core.boundedcontext.inbox

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.core.boundedcontext.BoundedContext
import com.jimbroze.kbus.core.boundedcontext.BoundedContextId
import com.jimbroze.kbus.core.boundedcontext.ContextRuntime
import com.jimbroze.kbus.core.fixtures.RecordingInboxStore
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.ThrowingIntegrationEventHandler
import com.jimbroze.kbus.core.fixtures.emptyContextFactory
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class InboxCoordinatorTest {
    private fun context(
        id: BoundedContextId,
        dispatcherScope: CoroutineScope,
        inbox: BoundedContextInbox? = null,
    ): ContextRuntime {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val eventDispatcher =
            EventDispatcher(
                locator::domainHandlersFor,
                emptyList(),
                dispatcherScope,
                contextFactory = emptyContextFactory(dispatcherScope),
            )
        return ContextRuntime(
            BoundedContext(id, locator, inbox),
            eventDispatcher = lazy { eventDispatcher },
        )
    }

    private fun honouringInbox(store: RecordingInboxStore = RecordingInboxStore()) =
        BoundedContextInbox(store, InboxAckPolicy.HonourEventStrategy)

    @Test
    fun `routes to a context directly when it declares no inbox`() = runTest {
        val alpha = context(BoundedContextId("alpha"), this)
        val beta = context(BoundedContextId("beta"), this)

        val coordinator = InboxCoordinator(null, listOf(alpha, beta), backgroundScope)

        assertEquals(listOf<Any>(alpha, beta), coordinator.destinations)
    }

    @Test
    fun `routes through an inbox only for the contexts that declare one`() = runTest {
        val alpha = context(BoundedContextId("alpha"), this, honouringInbox())
        val beta = context(BoundedContextId("beta"), this)

        val coordinator = InboxCoordinator(null, listOf(alpha, beta), backgroundScope)

        assertTrue(coordinator.destinations[0] is EventInbox)
        assertEquals(beta, coordinator.destinations[1])
    }

    @Test
    fun `reports itself enabled only when some context declares an inbox`() = runTest {
        val withoutInbox = context(BoundedContextId("alpha"), this)
        val withInbox = context(BoundedContextId("beta"), this, honouringInbox())

        assertFalse(InboxCoordinator(null, listOf(withoutInbox), backgroundScope).isEnabled)
        assertTrue(InboxCoordinator(null, listOf(withInbox), backgroundScope).isEnabled)
    }

    @Test
    fun `launches nothing when no context declares an inbox`() = runTest {
        val alpha = context(BoundedContextId("alpha"), this)
        val coordinator = InboxCoordinator(null, listOf(alpha), backgroundScope)

        coordinator.startConsuming()

        assertTrue(backgroundScope.coroutineContext[Job]!!.children.toList().isEmpty())
    }

    @Test
    fun `launches one pump per inbox`() = runTest {
        val alpha = context(BoundedContextId("alpha"), this, honouringInbox())
        val beta = context(BoundedContextId("beta"), this, honouringInbox())
        val coordinator = InboxCoordinator(null, listOf(alpha, beta), backgroundScope)

        coordinator.startConsuming()

        assertEquals(2, backgroundScope.coroutineContext[Job]!!.children.toList().size)
    }

    @Test
    fun `runs one pump per inbox however many times it is started`() = runTest {
        val alpha = context(BoundedContextId("alpha"), this, honouringInbox())
        val coordinator = InboxCoordinator(null, listOf(alpha), backgroundScope)

        coordinator.startConsuming()
        coordinator.startConsuming()

        assertEquals(1, backgroundScope.coroutineContext[Job]!!.children.toList().size)
    }

    @Test
    fun `dispatches envelopes a previous run left pending`() = runTest {
        val store = RecordingInboxStore()
        val alpha = context(BoundedContextId("alpha"), backgroundScope, honouringInbox(store))
        store.save(listOf(EventEnvelope.of(TestIntegrationEvent("from-before-crash"))))
        val config = InboxTuning(pollInterval = 10.milliseconds)
        val coordinator = InboxCoordinator(config, listOf(alpha), backgroundScope)

        coordinator.startConsuming()
        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertEquals(1, store.markedConsumed.size)
    }

    private fun throwingContext(
        id: BoundedContextId,
        attempts: MutableList<String>,
        dispatcherScope: CoroutineScope,
        inbox: BoundedContextInbox? = null,
    ): ContextRuntime {
        val stores = HandlerFactoryStoreCollection()
        stores.eventStore.registerHandlers(
            TestIntegrationEvent::class,
            listOf(
                EventHandlerFactory(ThrowingIntegrationEventHandler::class) {
                    ThrowingIntegrationEventHandler(attempts)
                }
            ),
        )
        val locator = PersistingHandlerLocator(stores)
        locator.integrationEventRegistrar.addEventHandlers(
            TestIntegrationEvent::class,
            listOf(ThrowingIntegrationEventHandler::class),
        )
        val eventDispatcher =
            EventDispatcher(
                locator::domainHandlersFor,
                emptyList(),
                dispatcherScope,
                contextFactory = emptyContextFactory(dispatcherScope),
            )
        return ContextRuntime(
            BoundedContext(id, locator, inbox),
            eventDispatcher = lazy { eventDispatcher },
        )
    }

    @Test
    fun `applies an acknowledgement policy override only to contexts declaring an inbox`() =
        runTest {
            val alphaAttempts = mutableListOf<String>()
            val betaAttempts = mutableListOf<String>()
            val alphaStore = RecordingInboxStore()
            val alpha =
                throwingContext(
                    BoundedContextId("alpha"),
                    alphaAttempts,
                    backgroundScope,
                    BoundedContextInbox(alphaStore, InboxAckPolicy.RequireHandlerSuccess),
                )
            val beta = throwingContext(BoundedContextId("beta"), betaAttempts, backgroundScope)

            val coordinator = InboxCoordinator(null, listOf(alpha, beta), backgroundScope)

            // alpha declares an inbox: RequireHandlerSuccess overrides FireAndForget, so a failing
            // handler leaves the envelope pending rather than acked.
            alphaStore.save(listOf(EventEnvelope.of(TestIntegrationEvent("via-alpha"))))
            (coordinator.destinations[0] as EventInbox).drain()

            assertEquals(listOf("via-alpha"), alphaAttempts)
            assertTrue(
                alphaStore.markedConsumed.isEmpty(),
                "RequireHandlerSuccess must not ack a failed handler",
            )

            // beta declares no inbox, so it is never overridden: it still honours the event's own
            // FireAndForget and swallows the failure.
            coordinator.destinations[1].deliver(
                listOf(EventEnvelope.of(TestIntegrationEvent("via-beta")))
            )

            assertEquals(listOf("via-beta"), betaAttempts)
        }
}
