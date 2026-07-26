package com.jimbroze.kbus.core.module.inbox

import com.jimbroze.kbus.contracts.messages.event.EventEnvelope
import com.jimbroze.kbus.core.fixtures.RecordingInboxStore
import com.jimbroze.kbus.core.fixtures.TestIntegrationEvent
import com.jimbroze.kbus.core.fixtures.emptyContextFactory
import com.jimbroze.kbus.core.messages.event.dispatch.EventDispatcher
import com.jimbroze.kbus.core.module.BoundedContext
import com.jimbroze.kbus.core.module.BoundedContextId
import com.jimbroze.kbus.core.module.LocatorSubscriptions
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    private fun context(id: BoundedContextId, dispatcherScope: CoroutineScope): BoundedContext {
        val locator = PersistingHandlerLocator(HandlerFactoryStoreCollection())
        val eventDispatcher =
            EventDispatcher(
                locator::handlersFor,
                emptyList(),
                dispatcherScope,
                contextFactory = emptyContextFactory(),
            )
        return BoundedContext(id, LocatorSubscriptions(locator), locator) { eventDispatcher }
    }

    @Test
    fun destinations_withNoConfig_areTheContextsThemselves() = runTest {
        val alpha = context(BoundedContextId("alpha"), this)
        val beta = context(BoundedContextId("beta"), this)

        val coordinator = InboxCoordinator(null, listOf(alpha, beta), backgroundScope)

        assertEquals(listOf<Any>(alpha, beta), coordinator.destinations)
    }

    @Test
    fun destinations_wrapsOnlyTheContextsThatHaveAConfiguredStore() = runTest {
        val alpha = context(BoundedContextId("alpha"), this)
        val beta = context(BoundedContextId("beta"), this)
        val config = InboxConfig(stores = mapOf(BoundedContextId("alpha") to RecordingInboxStore()))

        val coordinator = InboxCoordinator(config, listOf(alpha, beta), backgroundScope)

        assertTrue(coordinator.destinations[0] is EventInbox)
        assertEquals(beta, coordinator.destinations[1])
    }

    @Test
    fun isEnabled_isFalseWithNoConfig_trueWithOne() = runTest {
        val alpha = context(BoundedContextId("alpha"), this)
        val noConfig = InboxCoordinator(null, listOf(alpha), backgroundScope)
        val withConfig =
            InboxCoordinator(
                InboxConfig(stores = mapOf(BoundedContextId("alpha") to RecordingInboxStore())),
                listOf(alpha),
                backgroundScope,
            )

        assertFalse(noConfig.isEnabled)
        assertTrue(withConfig.isEnabled)
    }

    @Test
    fun construction_withAStoreForAnUnknownContext_throws() = runTest {
        val alpha = context(BoundedContextId("alpha"), this)
        val config =
            InboxConfig(stores = mapOf(BoundedContextId("unknown") to RecordingInboxStore()))

        val exception =
            assertFailsWith<IllegalArgumentException> {
                InboxCoordinator(config, listOf(alpha), backgroundScope)
            }
        assertTrue(exception.message!!.contains("unknown"))
    }

    @Test
    fun startConsuming_withNoConfig_launchesNothing() = runTest {
        val alpha = context(BoundedContextId("alpha"), this)
        val coordinator = InboxCoordinator(null, listOf(alpha), backgroundScope)

        coordinator.startConsuming()

        assertTrue(backgroundScope.coroutineContext[Job]!!.children.toList().isEmpty())
    }

    @Test
    fun startConsuming_launchesOnePumpPerInbox() = runTest {
        val alpha = context(BoundedContextId("alpha"), this)
        val beta = context(BoundedContextId("beta"), this)
        val config =
            InboxConfig(
                stores =
                    mapOf(
                        BoundedContextId("alpha") to RecordingInboxStore(),
                        BoundedContextId("beta") to RecordingInboxStore(),
                    )
            )
        val coordinator = InboxCoordinator(config, listOf(alpha, beta), backgroundScope)

        coordinator.startConsuming()

        assertEquals(2, backgroundScope.coroutineContext[Job]!!.children.toList().size)
    }

    @Test
    fun startConsuming_calledTwice_runsOnlyOnePumpPerInbox() = runTest {
        val alpha = context(BoundedContextId("alpha"), this)
        val config = InboxConfig(stores = mapOf(BoundedContextId("alpha") to RecordingInboxStore()))
        val coordinator = InboxCoordinator(config, listOf(alpha), backgroundScope)

        coordinator.startConsuming()
        coordinator.startConsuming()

        assertEquals(1, backgroundScope.coroutineContext[Job]!!.children.toList().size)
    }

    @Test
    fun startConsuming_dispatchesPendingEnvelopesLeftBehindByAPreviousRun() = runTest {
        val alpha = context(BoundedContextId("alpha"), backgroundScope)
        val store = RecordingInboxStore()
        store.save(listOf(EventEnvelope.of(TestIntegrationEvent("from-before-crash"))))
        val config =
            InboxConfig(
                stores = mapOf(BoundedContextId("alpha") to store),
                pollInterval = 10.milliseconds,
            )
        val coordinator = InboxCoordinator(config, listOf(alpha), backgroundScope)

        coordinator.startConsuming()
        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertEquals(1, store.markedConsumed.size)
    }
}
