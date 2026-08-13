package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.contracts.common.Message
import com.jimbroze.kbus.contracts.common.MissingHandlerException
import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.command.NestedTransactionMismatchException
import com.jimbroze.kbus.contracts.messages.event.IntegrationEvent
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.FailureReason
import com.jimbroze.kbus.contracts.result.GenericFailure
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.contracts.uow.TransactionConfig
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.boundedcontext.BoundedContext
import com.jimbroze.kbus.core.boundedcontext.BoundedContextId
import com.jimbroze.kbus.core.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.core.middleware.infrastructure.Middleware
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareHandler
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareInvocationContext
import com.jimbroze.kbus.core.middleware.infrastructure.MiddlewareScope
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.EventHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.domain.event.DispatchTiming
import com.jimbroze.kbus.domain.event.DomainEvent
import com.jimbroze.kbus.domain.event.DomainEventHandler
import com.jimbroze.kbus.domain.event.DomainEventPublisher
import com.jimbroze.kbus.testdoubles.advanceVirtualTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private data class NestedFailure(override val reason: FailureReason) : MessageFailure

private class InnerCommand(val name: String, val fail: Boolean = false) :
    Command<BusResult<String, MessageFailure>>()

private class InnerCommandHandler(
    private val domainEventPublisher: DomainEventPublisher,
    private val integrationEventPublisher: IntegrationEventPublisher,
    private val recorder: MutableList<String>,
    private val publishesIntegrationEvent: Boolean,
    private val publishesDomainEvent: Boolean,
    override val executeInTransaction: TransactionConfig?,
) : CommandHandler<InnerCommand, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: InnerCommand): BusResult<String, MessageFailure> {
        recorder.add("inner:${message.name}")
        if (message.fail) error("inner blew up")
        if (publishesDomainEvent) domainEventPublisher.publish(NestedDomainEvent(message.name))
        if (publishesIntegrationEvent)
            integrationEventPublisher.publish(listOf(NestedIntegrationEvent(message.name)))

        return BusResult.success("inner:${message.name}")
    }
}

private class FailingInnerCommand : Command<BusResult<String, MessageFailure>>()

private class FailingInnerCommandHandler :
    CommandHandler<FailingInnerCommand, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: FailingInnerCommand): BusResult<String, MessageFailure> =
        BusResult.failure(NestedFailure(GenericFailure("inner declined")))
}

private class OuterCommand(val name: String, val innerFails: Boolean = false) :
    Command<BusResult<String, MessageFailure>>()

private class OuterCommandHandler(
    private val commandExecutor: NestedCommandExecutor,
    private val recorder: MutableList<String>,
    override val executeInTransaction: TransactionConfig?,
) : CommandHandler<OuterCommand, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: OuterCommand): BusResult<String, MessageFailure> {
        recorder.add("outer:${message.name}")
        val innerResult = commandExecutor.execute(InnerCommand(message.name, message.innerFails))

        return innerResult
    }
}

/** Returns the nested command's failure as its own, so a caller can see it was not swallowed. */
private class FailureRelayingOuterCommand : Command<BusResult<String, MessageFailure>>()

private class FailureRelayingOuterCommandHandler(
    private val commandExecutor: NestedCommandExecutor,
    private val recorder: MutableList<String>,
) : CommandHandler<FailureRelayingOuterCommand, BusResult<String, MessageFailure>>() {
    override suspend fun handle(
        message: FailureRelayingOuterCommand
    ): BusResult<String, MessageFailure> {
        val innerResult = commandExecutor.execute(FailingInnerCommand())
        recorder.add("outer-continued")

        return innerResult
    }
}

private class MiddleCommand(val name: String) : Command<BusResult<String, MessageFailure>>()

private class MiddleCommandHandler(private val commandExecutor: NestedCommandExecutor) :
    CommandHandler<MiddleCommand, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: MiddleCommand): BusResult<String, MessageFailure> =
        commandExecutor.execute(InnerCommand(message.name))
}

private class DepthTwoCommand(val name: String) : Command<BusResult<String, MessageFailure>>()

private class DepthTwoCommandHandler(private val commandExecutor: NestedCommandExecutor) :
    CommandHandler<DepthTwoCommand, BusResult<String, MessageFailure>>() {
    override val executeInTransaction = TransactionConfig()

    override suspend fun handle(message: DepthTwoCommand): BusResult<String, MessageFailure> =
        commandExecutor.execute(MiddleCommand(message.name))
}

/** Executes a command owned by another context, which this context's locator cannot resolve. */
private class ForeignCommand : Command<BusResult<String, MessageFailure>>()

private class ForeignCommandHandler :
    CommandHandler<ForeignCommand, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: ForeignCommand): BusResult<String, MessageFailure> =
        BusResult.success("foreign")
}

private class CrossContextCommand : Command<BusResult<String, MessageFailure>>()

private class CrossContextCommandHandler(private val commandExecutor: NestedCommandExecutor) :
    CommandHandler<CrossContextCommand, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: CrossContextCommand): BusResult<String, MessageFailure> =
        commandExecutor.execute(ForeignCommand())
}

private class NestedDomainEvent(val name: String) : DomainEvent()

private class NestedDomainEventHandler(private val recorder: MutableList<String>) :
    DomainEventHandler<NestedDomainEvent>() {
    override val dispatchTiming = DispatchTiming.AtEndOfTransaction

    override suspend fun handle(message: NestedDomainEvent) {
        recorder.add("domain:${message.name}")
    }
}

private class NestedIntegrationEvent(val name: String) : IntegrationEvent()

private class NestedIntegrationEventHandler(private val recorder: MutableList<String>) :
    IntegrationEventHandler<NestedIntegrationEvent> {
    override suspend fun handle(message: NestedIntegrationEvent) {
        recorder.add("integration:${message.name}")
    }
}

private class RecordingMiddleware(
    private val label: String,
    override val scope: MiddlewareScope,
    private val recorder: MutableList<String>,
) : Middleware {
    override suspend fun <TMessage : Message, TResult> handle(
        message: TMessage,
        context: MiddlewareInvocationContext,
        nextMiddleware: MiddlewareHandler<TMessage, TResult>,
    ): TResult {
        if (message is Command<*>) recorder.add("$label:${message::class.simpleName}")

        return nextMiddleware(message)
    }
}

/** Records each transaction it runs, and whether that transaction reached its end. */
private class RecordingTransactionManager(private val recorder: MutableList<String>) :
    TransactionManager {
    var executions = 0
        private set

    override suspend fun <TResult> execute(block: suspend () -> TResult): TResult {
        executions++
        recorder.add("transaction-begin")
        try {
            val result = block()
            recorder.add("commit")
            return result
        } catch (exception: Throwable) {
            recorder.add("rollback")
            throw exception
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageBusNestedCommandTest {
    private val recorder = mutableListOf<String>()

    private fun registerOuterAndInner(
        stores: HandlerFactoryStoreCollection,
        outerTransaction: TransactionConfig? = TransactionConfig(),
        innerTransaction: TransactionConfig? = TransactionConfig(),
        innerPublishesIntegrationEvent: Boolean = false,
        innerPublishesDomainEvent: Boolean = false,
    ) {
        stores.commandStore.registerHandlers(
            OuterCommand::class,
            listOf(
                CommandHandlerFactory(OuterCommandHandler::class) { deps ->
                    OuterCommandHandler(deps.commandExecutor, recorder, outerTransaction)
                }
            ),
        )
        stores.commandStore.registerHandlers(
            InnerCommand::class,
            listOf(
                CommandHandlerFactory(InnerCommandHandler::class) { deps ->
                    InnerCommandHandler(
                        deps.domainEventPublisher,
                        deps.integrationEventPublisher,
                        recorder,
                        innerPublishesIntegrationEvent,
                        innerPublishesDomainEvent,
                        innerTransaction,
                    )
                }
            ),
        )
    }

    @Test
    fun `runs a nested command in the outer command's transaction`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val transactionManager = RecordingTransactionManager(recorder)
        registerOuterAndInner(stores)

        val bus =
            MessageBus(locator, transactionManager = transactionManager, appScope = backgroundScope)

        val result = bus.execute(OuterCommand("shared"))

        assertEquals(BusResult.success("inner:shared"), result)
        assertEquals(1, transactionManager.executions)
        assertContentEquals(
            listOf("transaction-begin", "outer:shared", "inner:shared", "commit"),
            recorder,
        )
    }

    @Test
    fun `rolls the outer transaction back when a nested command throws`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val transactionManager = RecordingTransactionManager(recorder)
        registerOuterAndInner(stores)

        val bus =
            MessageBus(locator, transactionManager = transactionManager, appScope = backgroundScope)

        assertFailsWith<IllegalStateException> {
            bus.execute(OuterCommand("boom", innerFails = true))
        }

        assertEquals(1, transactionManager.executions)
        assertContentEquals(
            listOf("transaction-begin", "outer:boom", "inner:boom", "rollback"),
            recorder,
        )
    }

    @Test
    fun `runs a nested command's domain events in the outer transaction`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val transactionManager = RecordingTransactionManager(recorder)
        registerOuterAndInner(stores, innerPublishesDomainEvent = true)
        stores.eventStore.registerHandlers(
            NestedDomainEvent::class,
            listOf(
                EventHandlerFactory(NestedDomainEventHandler::class) {
                    NestedDomainEventHandler(recorder)
                }
            ),
        )
        locator.domainEventRegistrar.addDomainHandlers(
            NestedDomainEvent::class,
            listOf(NestedDomainEventHandler::class),
        )

        val bus =
            MessageBus(locator, transactionManager = transactionManager, appScope = backgroundScope)

        bus.execute(OuterCommand("domain"))

        assertContentEquals(
            listOf("transaction-begin", "outer:domain", "inner:domain", "domain:domain", "commit"),
            recorder,
        )
    }

    @Test
    fun `publishes a nested command's integration events through the outer invocation`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val transactionManager = RecordingTransactionManager(recorder)
        registerOuterAndInner(stores, innerPublishesIntegrationEvent = true)
        stores.eventStore.registerHandlers(
            NestedIntegrationEvent::class,
            listOf(
                EventHandlerFactory(NestedIntegrationEventHandler::class) {
                    NestedIntegrationEventHandler(recorder)
                }
            ),
        )
        locator.integrationEventRegistrar.addEventHandlers(
            NestedIntegrationEvent::class,
            listOf(NestedIntegrationEventHandler::class),
        )

        val bus =
            MessageBus(locator, transactionManager = transactionManager, appScope = backgroundScope)

        bus.execute(OuterCommand("integration"))
        advanceUntilIdle()
        advanceVirtualTime(100)

        assertEquals(listOf("integration:integration"), recorder.filter { it.startsWith("integ") })
        assertEquals(
            listOf("commit", "integration:integration"),
            recorder.filter { it == "commit" || it.startsWith("integration:") },
        )
    }

    @Test
    fun `runs every middleware for a nested command except those scoped to the entry point`() =
        runTest {
            val stores = HandlerFactoryStoreCollection()
            val locator = PersistingHandlerLocator(stores)
            val middlewareCalls = mutableListOf<String>()
            registerOuterAndInner(stores, outerTransaction = null, innerTransaction = null)

            val bus =
                MessageBus(
                    locator,
                    middlewares =
                        listOf(
                            RecordingMiddleware(
                                "entry",
                                MiddlewareScope.EntryPointOnly,
                                middlewareCalls,
                            ),
                            RecordingMiddleware(
                                "every",
                                MiddlewareScope.EveryCommand,
                                middlewareCalls,
                            ),
                        ),
                    appScope = backgroundScope,
                )

            bus.execute(OuterCommand("middleware"))

            assertContentEquals(
                listOf("entry:OuterCommand", "every:OuterCommand", "every:InnerCommand"),
                middlewareCalls,
            )
        }

    @Test
    fun `runs commands nested two deep in the one transaction`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val transactionManager = RecordingTransactionManager(recorder)
        registerOuterAndInner(stores, innerTransaction = null)
        stores.commandStore.registerHandlers(
            DepthTwoCommand::class,
            listOf(
                CommandHandlerFactory(DepthTwoCommandHandler::class) { deps ->
                    DepthTwoCommandHandler(deps.commandExecutor)
                }
            ),
        )
        stores.commandStore.registerHandlers(
            MiddleCommand::class,
            listOf(
                CommandHandlerFactory(MiddleCommandHandler::class) { deps ->
                    MiddleCommandHandler(deps.commandExecutor)
                }
            ),
        )

        val bus =
            MessageBus(locator, transactionManager = transactionManager, appScope = backgroundScope)

        val result = bus.execute(DepthTwoCommand("deep"))

        assertEquals(BusResult.success("inner:deep"), result)
        assertEquals(1, transactionManager.executions)
    }

    @Test
    fun `returns a nested command's failure to its caller without aborting it`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        stores.commandStore.registerHandlers(
            FailureRelayingOuterCommand::class,
            listOf(
                CommandHandlerFactory(FailureRelayingOuterCommandHandler::class) { deps ->
                    FailureRelayingOuterCommandHandler(deps.commandExecutor, recorder)
                }
            ),
        )
        stores.commandStore.registerHandlers(
            FailingInnerCommand::class,
            listOf(
                CommandHandlerFactory(FailingInnerCommandHandler::class) {
                    FailingInnerCommandHandler()
                }
            ),
        )

        val bus = MessageBus(locator, appScope = backgroundScope)

        val result = bus.execute(FailureRelayingOuterCommand())

        assertEquals("inner declined", result.failureOrNull()?.reason?.message)
        assertContentEquals(listOf("outer-continued"), recorder)
    }

    @Test
    fun `refuses to nest a command another context owns`() = runTest {
        val callerStores = HandlerFactoryStoreCollection()
        val callerLocator = PersistingHandlerLocator(callerStores)
        callerStores.commandStore.registerHandlers(
            CrossContextCommand::class,
            listOf(
                CommandHandlerFactory(CrossContextCommandHandler::class) { deps ->
                    CrossContextCommandHandler(deps.commandExecutor)
                }
            ),
        )
        val foreignStores = HandlerFactoryStoreCollection()
        val foreignLocator = PersistingHandlerLocator(foreignStores)
        foreignStores.commandStore.registerHandlers(
            ForeignCommand::class,
            listOf(CommandHandlerFactory(ForeignCommandHandler::class) { ForeignCommandHandler() }),
        )

        val bus =
            MessageBus(
                appScope = backgroundScope,
                contexts =
                    listOf(
                        BoundedContext(BoundedContextId("caller"), callerLocator),
                        BoundedContext(BoundedContextId("foreign"), foreignLocator),
                    ),
            )

        assertFailsWith<MissingHandlerException> { bus.execute(CrossContextCommand()) }
    }

    @Test
    fun `runs a nested command declaring no transaction inside the outer one`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val transactionManager = RecordingTransactionManager(recorder)
        registerOuterAndInner(stores, innerTransaction = null)

        val bus =
            MessageBus(locator, transactionManager = transactionManager, appScope = backgroundScope)

        val result = bus.execute(OuterCommand("indifferent"))

        assertEquals(BusResult.success("inner:indifferent"), result)
        assertEquals(1, transactionManager.executions)
    }

    @Test
    fun `refuses a command declaring a transaction when nested outside one`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        registerOuterAndInner(stores, outerTransaction = null)

        val bus = MessageBus(locator, appScope = backgroundScope)

        assertFailsWith<NestedTransactionMismatchException> {
            bus.execute(OuterCommand("untransacted"))
        }
        assertContentEquals(listOf("outer:untransacted"), recorder)
    }

    @Test
    fun `runs a nested command declaring the transaction manager already running`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val transactionManager = RecordingTransactionManager(recorder)
        registerOuterAndInner(
            stores,
            outerTransaction = TransactionConfig(transactionManagerOverride = transactionManager),
            innerTransaction = TransactionConfig(transactionManagerOverride = transactionManager),
        )

        val bus = MessageBus(locator, appScope = backgroundScope)

        val result = bus.execute(OuterCommand("same-manager"))

        assertEquals(BusResult.success("inner:same-manager"), result)
        assertEquals(1, transactionManager.executions)
    }

    @Test
    fun `refuses a nested command declaring a different transaction manager`() = runTest {
        val stores = HandlerFactoryStoreCollection()
        val locator = PersistingHandlerLocator(stores)
        val outerManager = RecordingTransactionManager(recorder)
        val otherManager = RecordingTransactionManager(mutableListOf())
        registerOuterAndInner(
            stores,
            outerTransaction = TransactionConfig(transactionManagerOverride = outerManager),
            innerTransaction = TransactionConfig(transactionManagerOverride = otherManager),
        )

        val bus = MessageBus(locator, appScope = backgroundScope)

        assertFailsWith<NestedTransactionMismatchException> {
            bus.execute(OuterCommand("other-manager"))
        }
        assertEquals(0, otherManager.executions)
        assertContentEquals(
            listOf("transaction-begin", "outer:other-manager", "rollback"),
            recorder,
        )
    }
}
