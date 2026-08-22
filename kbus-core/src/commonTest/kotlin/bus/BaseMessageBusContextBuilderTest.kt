package com.jimbroze.kbus.core.bus

import com.jimbroze.kbus.api.messages.command.Command
import com.jimbroze.kbus.api.messages.command.CommandHandler
import com.jimbroze.kbus.api.result.BusResult
import com.jimbroze.kbus.api.result.MessageFailure
import com.jimbroze.kbus.application.messages.command.CommandDependencies
import com.jimbroze.kbus.application.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.core.boundedcontext.BoundedContext
import com.jimbroze.kbus.core.boundedcontext.BoundedContextId
import com.jimbroze.kbus.core.boundedcontext.CommandOwningContext
import com.jimbroze.kbus.core.boundedcontext.ContextBuilder
import com.jimbroze.kbus.core.registry.persisting.PersistingHandlerLocator
import com.jimbroze.kbus.core.registry.persisting.store.CommandHandlerFactory
import com.jimbroze.kbus.core.registry.persisting.store.HandlerFactoryStoreCollection
import com.jimbroze.kbus.infrastructure.transaction.adapters.EmptyTransactionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

private class AlphaCommand : Command<BusResult<String, MessageFailure>>()

private class AlphaCommandHandler :
    CommandHandler<AlphaCommand, BusResult<String, MessageFailure>>() {
    override suspend fun handle(message: AlphaCommand): BusResult<String, MessageFailure> =
        BusResult.success("handled")
}

/** The shape a generated bus takes: contexts held by name, built through the builder. */
private class NamedContextBus(
    buildContexts: (ContextBuilder) -> Contexts,
    appScope: CoroutineScope,
) :
    BaseMessageBus<NamedContextBus.Contexts>(
        buildContexts,
        EmptyTransactionManager(),
        emptyList(),
        appScope,
    ) {
    class Contexts(builder: ContextBuilder, alphaLocator: PersistingHandlerLocator) {
        val alpha: CommandOwningContext<NestedCommandExecutor> =
            builder.register(BoundedContext(BoundedContextId("alpha"), alphaLocator))
    }

    suspend fun executeAlpha(command: AlphaCommand): BusResult<String, MessageFailure> {
        val handlerCreator = { commandDependencies: CommandDependencies, _: NestedCommandExecutor ->
            boundedContexts.alpha.handlerFor(command, commandDependencies)!!
        }
        return commandExecutor.execute(command, boundedContexts.alpha, handlerCreator)
    }
}

private class NoContextBus(appScope: CoroutineScope) :
    BaseMessageBus<Unit>({}, EmptyTransactionManager(), emptyList(), appScope)

class BaseMessageBusContextBuilderTest {
    private fun locatorWithAlphaCommand(): PersistingHandlerLocator {
        val stores = HandlerFactoryStoreCollection()
        stores.commandStore.registerHandlers(
            AlphaCommand::class,
            listOf(CommandHandlerFactory(AlphaCommandHandler::class) { AlphaCommandHandler() }),
        )
        return PersistingHandlerLocator(stores)
    }

    @Test
    fun `runs the commands of a context registered through it`() = runTest {
        val locator = locatorWithAlphaCommand()
        val bus =
            NamedContextBus(
                { builder -> NamedContextBus.Contexts(builder, locator) },
                backgroundScope,
            )

        val result = bus.executeAlpha(AlphaCommand())

        assertTrue(result.isSuccess)
        assertEquals("handled", result.getOrNull())
    }

    @Test
    fun `refuses two contexts registered under the same id`() = runTest {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                NamedContextBus(
                    { builder ->
                        builder.register(
                            BoundedContext(BoundedContextId("alpha"), PersistingHandlerLocator())
                        )
                        NamedContextBus.Contexts(builder, locatorWithAlphaCommand())
                    },
                    backgroundScope,
                )
            }

        assertTrue(exception.message!!.contains("alpha"))
    }

    @Test
    fun `refuses to build a bus with no context registered`() = runTest {
        val exception = assertFailsWith<IllegalArgumentException> { NoContextBus(backgroundScope) }

        assertTrue(exception.message!!.contains("at least one bounded context"))
    }
}
