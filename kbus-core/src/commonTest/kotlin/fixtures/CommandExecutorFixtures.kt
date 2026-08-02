package com.jimbroze.kbus.core.fixtures

import com.jimbroze.kbus.contracts.messages.command.Command
import com.jimbroze.kbus.contracts.messages.command.CommandHandler
import com.jimbroze.kbus.contracts.messages.event.IntegrationEventPublisher
import com.jimbroze.kbus.contracts.result.BusResult
import com.jimbroze.kbus.contracts.result.KBusResult
import com.jimbroze.kbus.contracts.result.MessageFailure
import com.jimbroze.kbus.contracts.uow.TransactionConfig
import com.jimbroze.kbus.contracts.uow.TransactionManager
import com.jimbroze.kbus.core.messages.command.CommandDependencies
import com.jimbroze.kbus.core.messages.command.CommandDependenciesFactory
import com.jimbroze.kbus.core.messages.command.CommandInvocation
import com.jimbroze.kbus.core.messages.command.NestedCommandExecutor
import com.jimbroze.kbus.core.module.OwningContext
import com.jimbroze.kbus.core.uow.UnitOfWork

class TestCommandDependenciesFactory : CommandDependenciesFactory {
    var unitOfWork: UnitOfWork<*>? = null
    var commandDependencies: CommandDependencies? = null

    override fun create(
        owningContext: OwningContext,
        invocation: CommandInvocation<*>,
        nestedCommandExecutor: NestedCommandExecutor,
    ): CommandDependencies {
        if (this.unitOfWork !== null) {
            error("Unit of work has already been set")
        }

        val commandDependencies =
            CommandDependencies(
                TestDomainEventPublisher(),
                nestedCommandExecutor,
                invocation.integrationEventPublisher,
            )

        this.unitOfWork = invocation.unitOfWork
        this.commandDependencies = commandDependencies

        return commandDependencies
    }
}

fun <TResult> testCommandDependencies() =
    TestCommandDependenciesFactory()
        .create(TestOwningContext(), testInvocation<TResult>(), NoNestedCommandExecutor)

/** For handlers under test that never nest a command. */
object NoNestedCommandExecutor : NestedCommandExecutor {
    override suspend fun <TCommand : Command<TResult>, TResult : KBusResult> execute(
        command: TCommand
    ): TResult = error("This test's handler is not expected to execute a nested command")
}

class DispatchingCommand : Command<BusResult<Unit, MessageFailure>>()

class DispatchingCommandHandler(private val integrationEventPublisher: IntegrationEventPublisher) :
    CommandHandler<DispatchingCommand, BusResult<Unit, MessageFailure>>() {
    override val executeInTransaction: TransactionConfig? = null

    override suspend fun handle(message: DispatchingCommand): BusResult<Unit, MessageFailure> {
        integrationEventPublisher.publish(listOf(TestIntegrationEvent("test-event")))
        return BusResult.success(Unit)
    }
}

class TransactionCommand(val message: String) : Command<BusResult<String, MessageFailure>>()

class TransactionCommandHandler(transactionManager: TransactionManager? = null) :
    CommandHandler<TransactionCommand, BusResult<String, MessageFailure>>() {
    override val executeInTransaction: TransactionConfig? =
        TransactionConfig(transactionManagerOverride = transactionManager)

    override suspend fun handle(message: TransactionCommand): BusResult<String, MessageFailure> {
        return BusResult.success(message.message)
    }
}
