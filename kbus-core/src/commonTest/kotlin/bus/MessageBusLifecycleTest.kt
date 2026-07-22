package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.core.fixtures.CapturingLifecycleMiddleware
import com.jimbroze.kbus.core.fixtures.RecordingOutboxStore
import com.jimbroze.kbus.core.fixtures.ReturnCommand
import com.jimbroze.kbus.core.fixtures.ReturnCommandHandler
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.core.uow.OutboxConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class MessageBusLifecycleTest {
    private fun busWithReturnCommand():
        Pair<PersistingHandlerLocator, HandlerFactoryStoreCollection> {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            ReturnCommand::class,
            listOf(CommandHandlerFactory(ReturnCommandHandler::class) { ReturnCommandHandler() }),
        )
        return PersistingHandlerLocator(stores) to stores
    }

    private suspend fun realDelay(millis: Long) =
        withContext(Dispatchers.Default) { delay(millis.milliseconds) }

    @Test
    fun lifecycle_aware_middleware_receives_scope_on_start_not_construction() = runTest {
        val middleware = CapturingLifecycleMiddleware()

        val bus = MessageBus(middlewares = listOf(middleware))
        assertNull(middleware.startContext)

        bus.start()

        assertNotNull(middleware.startContext)
        assertTrue(middleware.startContext!!.scope.isActive)
    }

    @Test
    fun multiple_lifecycle_middlewares_each_get_their_own_scope() = runTest {
        val middleware1 = CapturingLifecycleMiddleware("First")
        val middleware2 = CapturingLifecycleMiddleware("Second")

        val bus = MessageBus(middlewares = listOf(middleware1, middleware2))
        bus.start()

        val scope1 = middleware1.startContext!!.scope
        val scope2 = middleware2.startContext!!.scope

        assertTrue(scope1.isActive)
        assertTrue(scope2.isActive)

        val name1 = scope1.coroutineContext[CoroutineName]?.name
        val name2 = scope2.coroutineContext[CoroutineName]?.name
        assertEquals("KBus-Middleware-CapturingLifecycleMiddleware", name1)
        assertEquals("KBus-Middleware-CapturingLifecycleMiddleware", name2)
    }

    @Test
    fun cancelling_root_scope_cancels_middleware_scopes() = runTest {
        val middleware = CapturingLifecycleMiddleware()
        val rootScope = CoroutineScope(Dispatchers.Default)

        val bus = MessageBus(middlewares = listOf(middleware), rootScope = rootScope)
        bus.start()

        assertTrue(middleware.startContext!!.scope.isActive)

        rootScope.cancel()

        realDelay(50)

        assertFalse(middleware.startContext!!.scope.isActive)
    }

    @Test
    fun nothing_polls_before_start() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        MessageBus(
            locator,
            rootScope = backgroundScope,
            outbox = OutboxConfig(store = store, pollInterval = 10.milliseconds),
        )
        realDelay(50)

        assertTrue(store.fetchLimits.isEmpty())
    }

    @Test
    fun start_called_twice_launches_only_one_poller() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        val bus =
            MessageBus(
                locator,
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 10.seconds),
            )

        bus.start()
        bus.start()
        realDelay(50)

        assertEquals(1, store.fetchLimits.size)
    }

    @Test
    fun stop_halts_polling() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        val bus =
            MessageBus(
                locator,
                rootScope = backgroundScope,
                outbox = OutboxConfig(store = store, pollInterval = 20.milliseconds),
            )

        bus.start()
        realDelay(50)
        val pollsBeforeStop = store.fetchLimits.size
        assertTrue(pollsBeforeStop > 0)

        bus.stop()
        realDelay(100)

        assertEquals(pollsBeforeStop, store.fetchLimits.size)
    }

    @Test
    fun stop_calls_onStop() = runTest {
        val middleware = CapturingLifecycleMiddleware()
        val bus = MessageBus(middlewares = listOf(middleware))

        bus.start()
        bus.stop()

        assertTrue(middleware.stopped)
    }

    @Test
    fun stop_before_start_is_a_noop() = runTest {
        val middleware = CapturingLifecycleMiddleware()
        val bus = MessageBus(middlewares = listOf(middleware))

        bus.stop()

        assertFalse(middleware.stopped)
        assertNull(middleware.startContext)
    }

    @Test
    fun execute_throws_on_an_unstarted_bus_with_an_outbox() = runTest {
        val store = RecordingOutboxStore()
        val (locator, _) = busWithReturnCommand()

        val bus =
            MessageBus(locator, rootScope = backgroundScope, outbox = OutboxConfig(store = store))

        assertFailsWith<IllegalStateException> { bus.execute(ReturnCommand("test")) }
    }

    @Test
    fun execute_throws_on_an_unstarted_bus_with_lifecycle_aware_middleware() = runTest {
        val (locator, _) = busWithReturnCommand()
        val bus = MessageBus(locator, middlewares = listOf(CapturingLifecycleMiddleware()))

        assertFailsWith<IllegalStateException> { bus.execute(ReturnCommand("test")) }
    }

    @Test
    fun execute_still_works_unstarted_on_a_bus_with_neither_an_outbox_nor_lifecycle_middleware() =
        runTest {
            val (locator, _) = busWithReturnCommand()
            val bus = MessageBus(locator)

            val result = bus.execute(ReturnCommand("test"))

            assertTrue(result.isSuccess)
        }
}
